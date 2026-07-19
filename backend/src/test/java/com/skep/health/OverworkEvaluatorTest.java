package com.skep.health;

import com.skep.contract.RateType;
import com.skep.dailywork.DailyWorkLog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5-W4 3겹 — 과로 판정 경계: 연속 야간 3일+ / 최근 7일 60h+. 순수 함수 검증.
 */
class OverworkEvaluatorTest {

    private static final LocalDate D = LocalDate.of(2026, 7, 13);

    /** 야간 로그(otNight>0), 시각 미기록 → 명목 8h. */
    private DailyWorkLog night(LocalDate date) {
        DailyWorkLog l = DailyWorkLog.create(1L, 1L);
        l.setPersonId(10L);
        l.setWorkDate(date);
        l.setRateType(RateType.DAILY);
        l.setOtNight(BigDecimal.valueOf(2));
        return l;
    }

    /** 주간 로그(야간 아님) + 시작·종료 시각으로 근무시간 지정. */
    private DailyWorkLog day(LocalDate date, LocalTime start, LocalTime end) {
        DailyWorkLog l = DailyWorkLog.create(1L, 1L);
        l.setPersonId(10L);
        l.setWorkDate(date);
        l.setRateType(RateType.DAILY);
        l.setStartTime(start);
        l.setEndTime(end);
        return l;
    }

    @Test
    void threeConsecutiveNightsTriggers() {
        OverworkEvaluator.Verdict v = OverworkEvaluator.evaluate(
                List.of(night(D), night(D.plusDays(1)), night(D.plusDays(2))));
        assertTrue(v.triggered());
        assertEquals(3, v.consecutiveNights());
    }

    @Test
    void twoConsecutiveNightsDoesNotTrigger() {
        OverworkEvaluator.Verdict v = OverworkEvaluator.evaluate(
                List.of(night(D), night(D.plusDays(1))));
        assertFalse(v.triggered());   // 야간 2일 + 근무 16h < 60h.
        assertEquals(2, v.consecutiveNights());
    }

    @Test
    void nonConsecutiveNightsDoNotTrigger() {
        // 야간 3일이지만 캘린더 비연속(사이 하루 건너뜀) → 최장 연속 2.
        OverworkEvaluator.Verdict v = OverworkEvaluator.evaluate(
                List.of(night(D), night(D.plusDays(1)), night(D.plusDays(3))));
        assertFalse(v.triggered());
        assertEquals(2, v.consecutiveNights());
    }

    @Test
    void sixtyHoursExactlyTriggers() {
        // 6일 × 10h = 60h, 야간 아님 → 시간 규칙으로 트리거.
        List<DailyWorkLog> logs = List.of(
                day(D, LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(1), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(2), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(3), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(4), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(5), LocalTime.of(7, 0), LocalTime.of(17, 0)));
        OverworkEvaluator.Verdict v = OverworkEvaluator.evaluate(logs);
        assertTrue(v.triggered());
        assertEquals(60.0, v.weekHours(), 0.001);
        assertEquals(0, v.consecutiveNights());
    }

    @Test
    void fiftyNineHoursDoesNotTrigger() {
        // 5일 × 10h + 1일 × 9h = 59h → 미트리거.
        List<DailyWorkLog> logs = List.of(
                day(D, LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(1), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(2), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(3), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(4), LocalTime.of(7, 0), LocalTime.of(17, 0)),
                day(D.plusDays(5), LocalTime.of(8, 0), LocalTime.of(17, 0)));
        OverworkEvaluator.Verdict v = OverworkEvaluator.evaluate(logs);
        assertFalse(v.triggered());
        assertEquals(59.0, v.weekHours(), 0.001);
    }

    @Test
    void overnightSpanCrossesMidnight() {
        // 21:00 → 06:00 = 9h (자정 넘김 보정).
        assertEquals(9.0, OverworkEvaluator.workedHours(
                day(D, LocalTime.of(21, 0), LocalTime.of(6, 0))), 0.001);
    }

    @Test
    void nominalHoursWhenTimesMissing() {
        assertEquals(OverworkEvaluator.NOMINAL_DAY_HOURS, OverworkEvaluator.workedHours(night(D)), 0.001);
    }

    @Test
    void maxConsecutiveDaysCounts() {
        assertEquals(0, OverworkEvaluator.maxConsecutiveDays(List.of()));
        assertEquals(3, OverworkEvaluator.maxConsecutiveDays(
                List.of(D, D.plusDays(1), D.plusDays(2))));
        assertEquals(2, OverworkEvaluator.maxConsecutiveDays(
                List.of(D, D.plusDays(1), D.plusDays(5), D.plusDays(6), D.plusDays(8))));
    }
}
