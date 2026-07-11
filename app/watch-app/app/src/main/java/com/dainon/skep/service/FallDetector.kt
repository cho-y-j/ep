package com.dainon.skep.service

import kotlin.math.abs

/**
 * 낙상 융합 판정기 (순수 Kotlin, Android 비의존 → 유닛테스트 가능).
 *
 * 입력: 가속도 magnitude / 자이로 magnitude / 기압(hPa) — 각각 (시각ms, 값).
 * 파이프라인:
 *   1) dip(자유낙하) → 2) impact(충격)  = 기존 SensorService 트리거 로직과 동일
 *   3) impact 후 confirmMs 동안 증거 3종 수집 → verdict(CONFIRM/SUPPRESS)
 *
 * 증거 3종 (초기 임계 = 실기기 튜닝값):
 *   E_rot  : [dip-0.5s, impact+0.5s] 자이로 피크 ≥ gyroThreshold
 *   E_alt  : (impact후 평균 − dip전 평균) 기압 상승 ≥ pressureThreshold
 *   E_still: impact후 confirmMs 동안 |mag-9.81| 평균 < stillThreshold  (낙상 후 정지)
 *
 * verdict = CONFIRM  (evidenceCount ≥ 1)  /  SUPPRESS (증거 0 — 모든 가용센서 증거 실패)
 * fail-open: 센서 없음/샘플 없음 → 해당 증거 통과(FN 안전). 두 센서 모두 없으면 SUPPRESS 불가.
 *
 * enforce=false(Shadow): impact 즉시 onFallConfirmed() (기존 동작 그대로) + 확인창 뒤 증거 로그만.
 * enforce=true         : impact 즉시 트리거 안 함 → 확인창 뒤 verdict로 onFallConfirmed()/onFallSuppressed().
 */
class FallDetector(
    private val fallThreshold: Float,
    private val impactThreshold: Float,
    private val fallWindowMs: Long,
    private val gyroThreshold: Float,
    private val pressureThreshold: Float,
    private val stillThreshold: Float,
    private val confirmMs: Long,
    private val gyroAvailable: Boolean,
    private val pressureAvailable: Boolean,
    private val enforce: Boolean,
    private val onFallConfirmed: () -> Unit,
    private val onFallSuppressed: (Evidence) -> Unit,
    private val log: (String) -> Unit,
) {
    data class Evidence(
        val rot: Float, val rotPass: Boolean,
        val alt: Float, val altPass: Boolean,
        val still: Float, val stillPass: Boolean,
    ) {
        val evidenceCount: Int get() =
            (if (rotPass) 1 else 0) + (if (altPass) 1 else 0) + (if (stillPass) 1 else 0)
        val confirm: Boolean get() = evidenceCount >= 1
    }

    private companion object {
        const val ROT_PRE_MS = 500L      // dip 이전 자이로 창
        const val ROT_POST_MS = 500L     // impact 이후 자이로 창
        const val ALT_WIN_MS = 1000L     // 기압 평균 창 (dip전 / impact후)
        const val RETAIN_MS = 5000L      // 링버퍼 보존 (dip전 ~ impact후 커버)
    }

    private val accel = ArrayDeque<Pair<Long, Float>>()
    private val gyro = ArrayDeque<Pair<Long, Float>>()
    private val press = ArrayDeque<Pair<Long, Float>>()

    private var lastMag = 9.81f
    private var dipDetected = false
    private var dipTime = 0L
    private var confirming = false
    private var impactTime = 0L
    private var lastEvidence: Evidence? = null

    /** 직전 낙상의 증거≥1 여부 (Q4 옵션 C용). 증거 미산출(null)이면 fail-open=true(유지) */
    fun lastFallHadEvidence(): Boolean = (lastEvidence?.evidenceCount ?: 3) >= 1

    fun onAccel(t: Long, mag: Float) {
        accel.addLast(t to mag); trim(accel, t)

        if (confirming) {
            if (t - impactTime >= confirmMs) finishVerdict()
            lastMag = mag
            return
        }

        // 1) dip (자유낙하) — 이전값이 정상이어야 진동 노이즈 배제
        if (mag < fallThreshold && !dipDetected && lastMag > fallThreshold) {
            dipDetected = true
            dipTime = t
        }
        // 2) impact (충격) — dip 후 창 이내 큰 충격
        if (dipDetected) {
            val dt = t - dipTime
            when {
                dt < 50L -> { /* 진동 스파이크 필터: 최소 50ms */ }
                dt > fallWindowMs -> dipDetected = false      // 창 초과 → 낙상 아님
                mag > impactThreshold -> {
                    dipDetected = false
                    impactTime = t
                    confirming = true
                    if (!enforce) onFallConfirmed()           // Shadow: 즉시 트리거(기존 동작)
                }
            }
        }
        lastMag = mag
    }

    fun onGyro(t: Long, mag: Float) { gyro.addLast(t to mag); trim(gyro, t) }
    fun onPressure(t: Long, hPa: Float) { press.addLast(t to hPa); trim(press, t) }

    /** 오염 상태(EMERGENCY/미착용/수신/안정화/쿨다운)에서 호출 — 감지 상태 초기화 */
    fun reset() {
        lastMag = 9.81f
        dipDetected = false
        confirming = false
        accel.clear(); gyro.clear(); press.clear()
    }

    private fun finishVerdict() {
        val ev = computeEvidence()
        lastEvidence = ev
        log("FALL_EVIDENCE rot=%.2f(pass=%b) alt=%.2f(pass=%b) still=%.2f(pass=%b) verdict=%s".format(
            ev.rot, ev.rotPass, ev.alt, ev.altPass, ev.still, ev.stillPass,
            if (ev.confirm) "CONFIRM" else "SUPPRESS"))
        if (enforce) {
            if (ev.confirm) onFallConfirmed() else onFallSuppressed(ev)
        }
        confirming = false
        dipDetected = false
    }

    private fun computeEvidence(): Evidence {
        // E_rot — 자이로 피크
        var rot = 0f; var rotPass = true
        if (gyroAvailable) {
            val peak = gyro.filter { it.first in (dipTime - ROT_PRE_MS)..(impactTime + ROT_POST_MS) }
                .maxOfOrNull { it.second }
            if (peak != null) { rot = peak; rotPass = peak >= gyroThreshold }   // 샘플 없으면 fail-open
        }
        // E_alt — 기압 상승 (impact후 평균 − dip전 평균)
        var alt = 0f; var altPass = true
        if (pressureAvailable) {
            val pre = press.filter { it.first in (dipTime - ALT_WIN_MS)..dipTime }.map { it.second }
            val post = press.filter { it.first in impactTime..(impactTime + ALT_WIN_MS) }.map { it.second }
            if (pre.isNotEmpty() && post.isNotEmpty()) {
                alt = post.average().toFloat() - pre.average().toFloat()
                altPass = alt >= pressureThreshold
            }   // 샘플 부족 → fail-open
        }
        // E_still — impact 후 정지
        var still = 0f; var stillPass = true
        val stillWin = accel.filter { it.first > impactTime && it.first <= impactTime + confirmMs }
            .map { abs(it.second - 9.81f) }
        if (stillWin.isNotEmpty()) {
            still = stillWin.average().toFloat()
            stillPass = still < stillThreshold
        }   // 샘플 없으면 fail-open
        return Evidence(rot, rotPass, alt, altPass, still, stillPass)
    }

    private fun trim(buf: ArrayDeque<Pair<Long, Float>>, now: Long) {
        val cutoff = now - RETAIN_MS
        while (buf.isNotEmpty() && buf.first().first < cutoff) buf.removeFirst()
    }
}
