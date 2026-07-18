package com.dainon.skep.service

/**
 * P5-W0 저전력 전송 정책 — "침묵이 정상, 예외만 보고".
 * 서버(GET /api/field-auth/watch-policy)가 현장 폭염/강풍 맥락으로 가변, 폰이 /skep/policy 로 워치에 전달.
 * GREEN = 심박 60초 듀티·10분 묶음 전송, YELLOW = 30초 듀티·5분 묶음. RED(경보)는 워치 온디바이스 판정이라
 * 정책 대상 아님(실시간 flush 는 shouldFlush(realtime=true)).
 *
 * 순수 로직(단위 테스트 대상): 정책 적용 + 묶음 flush 시점 판정. SensorService 는 배선만.
 */
class WatchTelemetryPolicy {

    var state: String = "GREEN"; private set
    var sendIntervalMs: Long = 600_000L; private set   // 10분.
    var hrDutyMs: Long = 60_000L; private set          // 60초.

    /** 서버 정책 적용. 유효 범위 밖·누락 값은 무시(기존 유지). */
    fun apply(state: String?, sendIntervalSec: Int?, hrDutySec: Int?) {
        if (state == "GREEN" || state == "YELLOW") this.state = state
        if (sendIntervalSec != null && sendIntervalSec in 10..3600) sendIntervalMs = sendIntervalSec * 1000L
        if (hrDutySec != null && hrDutySec in 5..600) hrDutyMs = hrDutySec * 1000L
    }

    /** 묶음 전송할 때인가 — 보낼 게 있고(버퍼>0), 실시간(경보)이거나 전송 주기 경과. */
    fun shouldFlush(realtime: Boolean, bufferSize: Int, elapsedMs: Long): Boolean =
        bufferSize > 0 && (realtime || elapsedMs >= sendIntervalMs)
}
