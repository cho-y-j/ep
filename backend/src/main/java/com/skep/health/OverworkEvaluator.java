package com.skep.health;

import com.skep.dailywork.DailyWorkLog;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * P5-W4 3겹 — 과로 판정(순수·단위 테스트용). 일일 확인서(daily_work_logs) 기반.
 * 트리거: 연속 야간근무 3일+ (야간·철야 OT 있는 날의 최장 연속) 또는 최근 7일 합산 근무 60h+.
 * workedHours 는 시작·종료 시각 구간(자정 넘김 보정), 시각 미기록이면 명목 8h(보수적).
 */
public final class OverworkEvaluator {

    /** 연속 야간 임계(일). */
    public static final int NIGHT_STREAK_THRESHOLD = 3;
    /** 최근 7일 합산 근무 임계(시간). */
    public static final double WEEK_HOURS_THRESHOLD = 60.0;
    /** 시작·종료 시각 미기록 로그의 명목 근무시간. */
    public static final double NOMINAL_DAY_HOURS = 8.0;

    private OverworkEvaluator() {}

    /** 야간근무일 판정 — 야간(19-21:30) 또는 철야(21-06) OT 가 있으면 그 날은 야간. */
    public static boolean isNightLog(DailyWorkLog l) {
        return positive(l.getOtNight()) || positive(l.getOtOvernight());
    }

    /** 로그 1건의 근무시간(h). 시작·종료 있으면 그 구간(종료<시작=자정 넘김 → +24h), 없으면 명목 8h. */
    public static double workedHours(DailyWorkLog l) {
        LocalTime s = l.getStartTime(), e = l.getEndTime();
        if (s == null || e == null) return NOMINAL_DAY_HOURS;
        long min = Duration.between(s, e).toMinutes();
        if (min <= 0) min += 24 * 60;
        return min / 60.0;
    }

    /** 날짜 집합에서 캘린더상 최장 연속 길이(비어 있으면 0). */
    public static int maxConsecutiveDays(Collection<LocalDate> dates) {
        if (dates.isEmpty()) return 0;
        TreeSet<LocalDate> sorted = new TreeSet<>(dates);
        int best = 1, run = 1;
        LocalDate prev = null;
        for (LocalDate d : sorted) {
            if (prev != null) run = d.equals(prev.plusDays(1)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    public record Verdict(boolean triggered, int consecutiveNights, double weekHours, List<String> reasons) {}

    /** 한 작업자의 최근 7일 로그(창 필터는 호출자 몫)로 판정. */
    public static Verdict evaluate(List<DailyWorkLog> last7Days) {
        Set<LocalDate> nightDays = new HashSet<>();
        double weekHours = 0;
        for (DailyWorkLog l : last7Days) {
            if (isNightLog(l)) nightDays.add(l.getWorkDate());
            weekHours += workedHours(l);
        }
        int nights = maxConsecutiveDays(nightDays);
        List<String> reasons = new ArrayList<>();
        if (nights >= NIGHT_STREAK_THRESHOLD) reasons.add("연속 야간근무 " + nights + "일");
        if (weekHours >= WEEK_HOURS_THRESHOLD) reasons.add("최근 7일 근무 " + Math.round(weekHours) + "시간");
        return new Verdict(!reasons.isEmpty(), nights, weekHours, reasons);
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
