package com.skep.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P5-W4 1겹 — 혈압 판정 경계(기본 임계 160/100 주의·180/110 차단권고). 경계 포함(>=) 검증.
 */
class BpVerdictTest {

    private final BpThresholds t = BpThresholds.defaults();   // 160/100 · 180/110.

    @Test
    void normalBelowCaution() {
        assertEquals(BpVerdict.OK, BpVerdict.evaluate(159, 99, t));
        assertEquals(BpVerdict.OK, BpVerdict.evaluate(120, 80, t));
    }

    @Test
    void cautionAtBoundary_sysOrDia() {
        assertEquals(BpVerdict.CAUTION, BpVerdict.evaluate(160, 99, t));   // 수축기 경계.
        assertEquals(BpVerdict.CAUTION, BpVerdict.evaluate(159, 100, t));  // 이완기 경계.
        assertEquals(BpVerdict.CAUTION, BpVerdict.evaluate(179, 109, t));  // 차단 직전.
    }

    @Test
    void blockAtBoundary_sysOrDia() {
        assertEquals(BpVerdict.BLOCK, BpVerdict.evaluate(180, 109, t));    // 수축기 차단 경계.
        assertEquals(BpVerdict.BLOCK, BpVerdict.evaluate(179, 110, t));    // 이완기 차단 경계.
        assertEquals(BpVerdict.BLOCK, BpVerdict.evaluate(200, 120, t));
    }

    @Test
    void higherOfSysDiaWins() {
        // 이완기는 정상인데 수축기만 차단 임계 → BLOCK.
        assertEquals(BpVerdict.BLOCK, BpVerdict.evaluate(185, 80, t));
        // 수축기는 정상인데 이완기만 주의 임계 → CAUTION.
        assertEquals(BpVerdict.CAUTION, BpVerdict.evaluate(130, 105, t));
    }

    @Test
    void customThresholdsRespected() {
        BpThresholds strict = new BpThresholds(140, 90, 170, 100);
        assertEquals(BpVerdict.CAUTION, BpVerdict.evaluate(140, 85, strict));
        assertEquals(BpVerdict.BLOCK, BpVerdict.evaluate(170, 85, strict));
    }
}
