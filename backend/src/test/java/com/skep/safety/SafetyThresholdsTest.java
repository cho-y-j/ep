package com.skep.safety;

import com.skep.common.ApiException;
import com.skep.weather.HeatStage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3a §3.4 — 현장 안전설정 법정 완화 금지 가드 + HeatStage 오버라이드 회귀 방어.
 * 순수 로직(Spring 무관). 강화(더 엄격)는 허용, 완화(법정보다 느슨)는 400.
 */
class SafetyThresholdsTest {

    private static SafetyThresholds of(double caution, double warning, double danger, double extreme,
                                       int restInterval, int restDuration, double windMps) {
        return new SafetyThresholds(caution, warning, danger, extreme, restInterval, restDuration,
                14, 17, windMps, false, 250);
    }

    @Test
    void legalDefaultPasses() {
        assertDoesNotThrow(() -> SafetyThresholds.legalDefault().validateNotWeakerThanLegal());
    }

    @Test
    void raisingTempThresholdRejected() {
        // 중지 임계 38 → 40 (완화) 금지.
        ApiException ex = assertThrows(ApiException.class,
                () -> of(31, 33, 35, 40, 120, 20, 10).validateNotWeakerThanLegal());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("SAFETY_WEAKER_THAN_LEGAL", ex.getCode());
    }

    @Test
    void lengtheningRestIntervalRejected() {
        // 휴식 간격 120 → 180 (완화) 금지.
        ApiException ex = assertThrows(ApiException.class,
                () -> of(31, 33, 35, 38, 180, 20, 10).validateNotWeakerThanLegal());
        assertEquals("SAFETY_WEAKER_THAN_LEGAL", ex.getCode());
    }

    @Test
    void shorteningRestDurationRejected() {
        // 휴식 시간 20 → 10 (완화) 금지.
        ApiException ex = assertThrows(ApiException.class,
                () -> of(31, 33, 35, 38, 120, 10, 10).validateNotWeakerThanLegal());
        assertEquals("SAFETY_WEAKER_THAN_LEGAL", ex.getCode());
    }

    @Test
    void raisingWindThresholdRejected() {
        // 풍속 임계 10 → 15 (완화) 금지.
        ApiException ex = assertThrows(ApiException.class,
                () -> of(31, 33, 35, 38, 120, 20, 15).validateNotWeakerThanLegal());
        assertEquals("SAFETY_WEAKER_THAN_LEGAL", ex.getCode());
    }

    @Test
    void strengtheningAllowed() {
        // 경고 33→32, 간격 120→90, 시간 20→30, 풍속 10→8 : 전부 강화 = 통과.
        assertDoesNotThrow(() -> of(30, 32, 34, 37, 90, 30, 8).validateNotWeakerThanLegal());
    }

    @Test
    void stageOfUsesOverriddenThresholds() {
        SafetyThresholds th = of(30, 32, 34, 37, 120, 20, 10);
        assertEquals(HeatStage.WARNING, th.stageOf(32.5)); // 32 임계로 낮춤 → 32.5 는 경고.
        assertEquals(HeatStage.CAUTION, th.stageOf(31.0)); // 30 주의, 32 경고 → 31 은 주의.
        assertEquals(HeatStage.NONE, th.stageOf(29.9));
    }

    @Test
    void intervalIsStricterOfLegalAndSetting() {
        SafetyThresholds th = of(31, 33, 35, 38, 90, 20, 10);
        assertEquals(90, th.intervalMinutes(HeatStage.WARNING)); // min(120, 90)
        assertEquals(60, th.intervalMinutes(HeatStage.DANGER));  // min(60, 90) — 약화 없음
        assertEquals(240, th.intervalMinutes(HeatStage.NONE));   // 근로기준법 고정
    }
}
