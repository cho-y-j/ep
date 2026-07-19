package com.skep.safety;

import com.skep.weather.HeatStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5-W0 워치 원격 정책 — 기상 맥락 YELLOW 판정 + 센서 상태 → 관제 상태등 매핑 순수 로직.
 */
class WatchPolicyServiceTest {

    @Test
    void calmContextIsGreen() {
        assertFalse(WatchPolicyService.isElevated(HeatStage.NONE, false));
        assertFalse(WatchPolicyService.isElevated(HeatStage.CAUTION, false));   // 폭염주의보는 아직 GREEN(W0 최소).
    }

    @Test
    void heatWarningOrAboveIsElevated() {
        assertTrue(WatchPolicyService.isElevated(HeatStage.WARNING, false));    // 폭염경보 33℃↑.
        assertTrue(WatchPolicyService.isElevated(HeatStage.DANGER, false));
        assertTrue(WatchPolicyService.isElevated(HeatStage.EXTREME, false));
    }

    @Test
    void windStopForcesElevatedRegardlessOfHeat() {
        assertTrue(WatchPolicyService.isElevated(HeatStage.NONE, true));
        assertTrue(WatchPolicyService.isElevated(HeatStage.CAUTION, true));
    }

    @Test
    void highRiskForcesElevatedRegardlessOfContext() {
        // P5-W4 2겹: 고위험군(HIGH)이면 평온한 기상에서도 YELLOW.
        assertTrue(WatchPolicyService.isElevated(HeatStage.NONE, false, true));
        assertTrue(WatchPolicyService.isElevated(HeatStage.CAUTION, false, true));
        // 위험군 아님 + 평온 = GREEN(기존 규칙 불변).
        assertFalse(WatchPolicyService.isElevated(HeatStage.NONE, false, false));
        assertFalse(WatchPolicyService.isElevated(HeatStage.CAUTION, false, false));
    }

    @Test
    void colorMapsBothNamingSchemes() {
        // WorkerState.name(워치 broadcast) 와 normal/caution/danger(직접 폴백) 둘 다.
        assertEquals("RED", WatchPolicyService.colorOf("EMERGENCY"));
        assertEquals("RED", WatchPolicyService.colorOf("FALL_DETECTED"));
        assertEquals("RED", WatchPolicyService.colorOf("danger"));
        assertEquals("YELLOW", WatchPolicyService.colorOf("MILD_ANOMALY"));
        assertEquals("YELLOW", WatchPolicyService.colorOf("WAITING_ACK"));
        assertEquals("YELLOW", WatchPolicyService.colorOf("caution"));
        assertEquals("GREEN", WatchPolicyService.colorOf("NORMAL"));
        assertEquals("GREEN", WatchPolicyService.colorOf("normal"));
        assertEquals("GREEN", WatchPolicyService.colorOf(null));
        assertEquals("GREEN", WatchPolicyService.colorOf(""));
    }
}
