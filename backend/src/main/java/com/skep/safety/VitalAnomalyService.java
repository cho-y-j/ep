package com.skep.safety;

import com.skep.field.FieldFcmService;
import com.skep.person.Person;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * P5-W1 서버 2차 판정 — sensor 배치 수신 시 개인 베이스라인 대비 맥락 결합 평가.
 * 3패턴(가용 데이터로만): (i) 지속 고심박+평상 (ii) 열스트레스 (iii) 급성 급락.
 * 전부 CAUTION — 긴급(RED) 승격 없음(긴급은 워치 온디바이스 낙상/SOS 전용). 활성 경보 있으면 스킵(중복 방지).
 * 온디바이스가 이미 발화한 상태(state != NORMAL)는 판정 제외 — 워치 1차 판정 보완만.
 *
 * 정직 고지: per-reading 가속도(활동량) 신호가 raw readings 에 없어 "저활동/무동작"은 직접 측정 불가.
 *  - (i) "작업 아님" ≈ 온디바이스 state 가 평상(NORMAL) + 개인 작업 상한 초과 지속으로 근사.
 *  - (iii) 운동성 급등은 오탐이라 제외, 실신·붕괴 신호인 급락만 판정. "무보고/무동작"은 데드맨(별개)이 커버.
 */
@Service
@RequiredArgsConstructor
public class VitalAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(VitalAnomalyService.class);

    /** 학습 보정 대상 kind — 낙상·SOS·데드맨·폭염크론은 여기 없음(민감도 저하 금지). */
    public static final List<String> LEARN_KINDS = List.of("vital_anomaly", "heat_risk");

    static final int CONSEC_N = 3;              // 지속 고심박 연속 표본 수.
    static final double ACUTE_DROP_RATIO = 0.40;
    static final int ACUTE_DROP_ABS = 25;       // 급락 최소 절대폭(bpm) — 노이즈 배제.
    static final double TEMP_RISE_C = 0.3;      // 열스트레스 체온 상승 추세 임계.
    static final int HR_RISE_BPM = 12;          // 열스트레스 심박 상승 추세 임계.

    private final FieldSafetyAlertRepository alertRepo;
    private final VitalBaselineService baselineService;
    private final WatchPolicyService watchPolicyService;
    private final SafetyAlertBroadcaster broadcaster;
    private final FieldFcmService fcm;

    /** 판정 입력 1건(순수 로직용). */
    public record VitalSample(Integer hr, Double bodyTemp, String state) {}

    /** 판정 결과 — 없으면 null. */
    public record Finding(String kind, String message) {}

    private static boolean isCalm(String state) {
        return state == null || "NORMAL".equalsIgnoreCase(state);
    }

    /**
     * 순수 판정(단위 테스트 대상). window 는 시간 오름차순. effWorkHrHigh=보정 반영 작업 상한(미학습=null).
     * heatElevated=현장 폭염단계 상향. 우선순위: 급락 → 지속 고심박 → 열스트레스.
     */
    public static Finding judge(List<VitalSample> window, Integer effWorkHrHigh, boolean heatElevated) {
        int n = window.size();
        if (n == 0) return null;
        VitalSample last = window.get(n - 1);
        if (!isCalm(last.state())) return null;   // 온디바이스 이미 발화 → 서버 2차 판정 제외.

        // (iii) 급성 급락 — 연속 표본 간 큰 하락(실신·붕괴 전조). 급등(운동성)은 제외.
        for (int i = 1; i < n; i++) {
            Integer a = window.get(i - 1).hr(), b = window.get(i).hr();
            if (a != null && b != null && a > 0 && b > 0 && isCalm(window.get(i).state())) {
                int drop = a - b;
                if (drop >= ACUTE_DROP_ABS && drop >= a * ACUTE_DROP_RATIO) {
                    return new Finding("vital_anomaly",
                            "급성 심박 급락 (" + a + "→" + b + "bpm) — 상태를 확인하세요");
                }
            }
        }

        // (i) 지속 고심박 — 최근 CONSEC_N 표본 모두 실효 작업 상한 초과 & 평상 → 쓰러짐 전조.
        if (effWorkHrHigh != null && n >= CONSEC_N) {
            boolean allHigh = true;
            for (VitalSample s : window.subList(n - CONSEC_N, n)) {
                if (s.hr() == null || s.hr() <= effWorkHrHigh || !isCalm(s.state())) { allHigh = false; break; }
            }
            if (allHigh) {
                return new Finding("vital_anomaly",
                        "지속 고심박 " + last.hr() + "bpm (개인 상한 " + effWorkHrHigh + " 초과) — 이상 징후, 상태를 확인하세요");
            }
        }

        // (ii) 열스트레스 — 폭염단계 상향 & 체온 상승 추세 & 심박 상승 추세 → 선제 휴식.
        if (heatElevated && n >= 2) {
            Double firstT = firstNonNullTemp(window), lastT = lastNonNullTemp(window);
            Integer firstH = firstNonNullHr(window), lastH = lastNonNullHr(window);
            boolean tempRose = firstT != null && lastT != null && (lastT - firstT) >= TEMP_RISE_C;
            boolean hrRose = firstH != null && lastH != null && (lastH - firstH) >= HR_RISE_BPM;
            if (tempRose && hrRose) {
                return new Finding("heat_risk", "열사병 전조 — 체온 상승 추세. 휴식하세요");
            }
        }
        return null;
    }

    // ── 배선 ────────────────────────────────────────────────

    /** sensor 배치 수신 후 호출(시간 오름차순 recent). 이상이면 CAUTION 경보 생성. */
    public void evaluate(Person p, Long siteId, Long bpCompanyId, List<FieldSensorReading> recent) {
        if (recent == null || recent.size() < 2) return;
        if (alertRepo.existsByPersonIdAndKindInAndResolvedFalse(p.getId(), LEARN_KINDS)) return;  // 중복 발화 방지.

        List<VitalSample> window = recent.stream()
                .map(r -> new VitalSample(r.getHr(),
                        r.getBodyTemp() != null ? r.getBodyTemp().doubleValue() : null, r.getState()))
                .toList();
        PersonVitalBaseline pv = baselineService.find(p.getId()).orElse(null);
        Integer effHigh = pv != null && pv.isLearned()
                ? VitalBaselineService.effectiveWorkHrHigh(pv.getWorkHrHigh(), pv.getAdjustPct()) : null;
        boolean heat = watchPolicyService.isHeatElevatedForSite(siteId);

        Finding f = judge(window, effHigh, heat);
        if (f == null) return;
        fire(p, siteId, bpCompanyId, f, recent.get(recent.size() - 1));
    }

    private void fire(Person p, Long siteId, Long bpCompanyId, Finding f, FieldSensorReading last) {
        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setPersonId(p.getId());
        a.setSiteId(siteId);
        a.setBpCompanyId(bpCompanyId);
        a.setKind(f.kind());
        a.setLevel("caution");
        a.setSeverity(SafetySeverity.CAUTION.name());   // 조기 경고 보조 — RED 승격 안 함.
        a.setMessage(p.getName() + " — " + f.message());
        a.setHr(last.getHr());
        a.setBodyTemp(last.getBodyTemp());
        alertRepo.save(a);
        broadcaster.publishCreated(a, p);   // 관제 WS(내 kind 는 level=caution 이라 여기서 FCM 재전파 없음).

        // 본인 폰 확인 요청(CAUTION·ack 필요) — 열면 [확인]이 자가취소=학습 오탐 피드백.
        if (p.getFcmToken() != null && !p.getFcmToken().isBlank()) {
            fcm.sendSafety(List.of(p.getFcmToken()), f.kind(), "상태 확인", a.getMessage(),
                    SafetySeverity.CAUTION.name(),
                    SafetyAlertClassifier.tts(f.kind(), SafetySeverity.CAUTION), true, a.getId());
        }
        log.info("VitalAnomaly fire person={} site={} kind={} alert={}", p.getId(), siteId, f.kind(), a.getId());
    }

    private static Double firstNonNullTemp(List<VitalSample> w) {
        for (VitalSample s : w) if (s.bodyTemp() != null) return s.bodyTemp();
        return null;
    }
    private static Double lastNonNullTemp(List<VitalSample> w) {
        for (int i = w.size() - 1; i >= 0; i--) if (w.get(i).bodyTemp() != null) return w.get(i).bodyTemp();
        return null;
    }
    private static Integer firstNonNullHr(List<VitalSample> w) {
        for (VitalSample s : w) if (s.hr() != null) return s.hr();
        return null;
    }
    private static Integer lastNonNullHr(List<VitalSample> w) {
        for (int i = w.size() - 1; i >= 0; i--) if (w.get(i).hr() != null) return w.get(i).hr();
        return null;
    }
}
