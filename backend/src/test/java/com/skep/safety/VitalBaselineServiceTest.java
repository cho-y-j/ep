package com.skep.safety;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5-W1 개인 대역 학습·자기보정 순수 로직 — 완화/강화 캡, 실효 상한, 분위수 대역, 자가취소 창.
 */
class VitalBaselineServiceTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Test
    void relaxStepsUpAndCapsAtPlus20() {
        assertEquals(0, bd("2").compareTo(VitalBaselineService.relax(bd("0"))));
        assertEquals(0, bd("20").compareTo(VitalBaselineService.relax(bd("18"))));
        assertEquals(0, bd("20").compareTo(VitalBaselineService.relax(bd("20"))));   // 상한 고정.
    }

    @Test
    void strengthenStepsDownAndFloorsAtMinus10() {
        assertEquals(0, bd("-2").compareTo(VitalBaselineService.strengthen(bd("0"))));
        assertEquals(0, bd("-10").compareTo(VitalBaselineService.strengthen(bd("-9"))));
        assertEquals(0, bd("-10").compareTo(VitalBaselineService.strengthen(bd("-10"))));  // 하한 고정.
    }

    @Test
    void effectiveWorkHrHighAppliesAdjust() {
        assertEquals(100, VitalBaselineService.effectiveWorkHrHigh(100, bd("0")));
        assertEquals(120, VitalBaselineService.effectiveWorkHrHigh(100, bd("20")));   // 완화 → 상향.
        assertEquals(90, VitalBaselineService.effectiveWorkHrHigh(100, bd("-10")));   // 강화 → 하향.
        assertNull(VitalBaselineService.effectiveWorkHrHigh(null, bd("5")));
    }

    @Test
    void learnBandNeedsMinSamples() {
        List<Integer> few = new ArrayList<>();
        for (int i = 0; i < VitalBaselineService.MIN_LEARN_SAMPLES - 1; i++) few.add(80);
        assertNull(VitalBaselineService.learnBand(few));   // 표본 부족 → 미학습.
    }

    @Test
    void learnBandComputesPercentiles() {
        List<Integer> vals = new ArrayList<>();
        for (int i = 1; i <= 40; i++) vals.add(i);   // 1..40 (>= MIN_LEARN_SAMPLES).
        VitalBaselineService.Band b = VitalBaselineService.learnBand(vals);
        assertEquals(3, b.restHrLow());     // p5.
        assertEquals(21, b.restHrHigh());   // p50.
        assertEquals(21, b.workHrLow());    // p50.
        assertEquals(36, b.workHrHigh());   // p90.
        assertEquals(40, b.sampleCount());
    }

    @Test
    void selfCancelWithinWindowOnly() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 19, 10, 0);
        assertTrue(VitalBaselineService.isSelfCancel(created, created.plusMinutes(10)));
        assertTrue(VitalBaselineService.isSelfCancel(created, created.plusMinutes(15)));   // 경계 포함.
        assertFalse(VitalBaselineService.isSelfCancel(created, created.plusMinutes(16)));
        assertFalse(VitalBaselineService.isSelfCancel(created, null));
        assertFalse(VitalBaselineService.isSelfCancel(null, created));
    }

    @Test
    void normalHrKeepsOnlyCalmValidReadings() {
        List<FieldSensorReading> rs = List.of(
                reading("NORMAL", 80), reading(null, 90),
                reading("MILD_ANOMALY", 200),   // 발화 상태 제외.
                reading("NORMAL", 0),            // 무효 HR 제외.
                reading("NORMAL", null));        // null 제외.
        assertEquals(List.of(80, 90), VitalBaselineService.normalHr(rs));
    }

    private static FieldSensorReading reading(String state, Integer hr) {
        FieldSensorReading r = new FieldSensorReading();
        r.setState(state);
        r.setHr(hr);
        return r;
    }
}
