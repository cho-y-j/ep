package com.skep.safety;

import com.skep.safety.VitalAnomalyService.Finding;
import com.skep.safety.VitalAnomalyService.VitalSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P5-W1 서버 2차 판정 3패턴 순수 로직 — 급락 / 지속 고심박 / 열스트레스 경계 + 평상 게이트 + 우선순위.
 */
class VitalAnomalyServiceTest {

    private static VitalSample s(Integer hr, Double temp, String state) { return new VitalSample(hr, temp, state); }
    private static VitalSample hr(Integer hr) { return s(hr, null, "NORMAL"); }

    @Test
    void emptyOrNonCalmLastYieldsNull() {
        assertNull(VitalAnomalyService.judge(List.of(), 100, false));
        // 최신 표본이 이미 온디바이스 발화 상태 → 서버 2차 판정 제외.
        assertNull(VitalAnomalyService.judge(List.of(hr(120), s(60, null, "MILD_ANOMALY")), 100, false));
    }

    @Test
    void acuteDropFiresBeyondBoundary() {
        // 급락 ≥40% & ≥25bpm (평상). 120→60: drop 60 ≥ 48, ≥25 → 발화.
        Finding f = VitalAnomalyService.judge(List.of(hr(120), hr(60)), null, false);
        assertEquals("vital_anomaly", f.kind());
    }

    @Test
    void acuteDropBelowBoundaryDoesNotFire() {
        // 100→61: drop 39 < 40%(=40) → 미발화.
        assertNull(VitalAnomalyService.judge(List.of(hr(100), hr(61)), null, false));
        // 100→74: drop 26 ≥25 이지만 <40% → 미발화(둘 다 충족해야).
        assertNull(VitalAnomalyService.judge(List.of(hr(100), hr(74)), null, false));
    }

    @Test
    void sustainedHighNeedsAllConsecutiveAboveEffectiveHigh() {
        // effHigh=100, 최근 3표본 모두 초과 → 발화.
        Finding f = VitalAnomalyService.judge(List.of(hr(110), hr(115), hr(120)), 100, false);
        assertEquals("vital_anomaly", f.kind());
        // 하나가 상한 이하(=100, not >100) → 미발화.
        assertNull(VitalAnomalyService.judge(List.of(hr(110), hr(100), hr(120)), 100, false));
        // 미학습(effHigh null) → 지속 고심박 판정 스킵.
        assertNull(VitalAnomalyService.judge(List.of(hr(110), hr(115), hr(120)), null, false));
    }

    @Test
    void heatStressNeedsElevatedAndBothRisingTrends() {
        // 폭염 상향 + 체온 +0.4 + 심박 +15 → 열사병 전조.
        List<VitalSample> w = List.of(s(90, 36.5, "NORMAL"), s(105, 36.9, "NORMAL"));
        assertEquals("heat_risk", VitalAnomalyService.judge(w, null, true).kind());
        // 폭염 미상향 → 미발화.
        assertNull(VitalAnomalyService.judge(w, null, false));
        // 체온 상승 부족(+0.2) → 미발화.
        assertNull(VitalAnomalyService.judge(List.of(s(90, 36.5, "NORMAL"), s(105, 36.7, "NORMAL")), null, true));
        // 심박 상승 부족(+8) → 미발화.
        assertNull(VitalAnomalyService.judge(List.of(s(90, 36.5, "NORMAL"), s(98, 36.9, "NORMAL")), null, true));
    }

    @Test
    void acuteDropTakesPriorityOverHeat() {
        // 창 전체로는 상승추세(열스트레스 성립: 체온+0.5·심박+30)지만 말미에 급락 → 더 급성인 급락 우선.
        List<VitalSample> w = List.of(
                s(50, 36.5, "NORMAL"), s(140, 36.6, "NORMAL"), s(80, 37.0, "NORMAL"));
        assertEquals("vital_anomaly", VitalAnomalyService.judge(w, null, true).kind());
    }
}
