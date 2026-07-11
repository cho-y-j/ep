package com.skep.settlement;

/**
 * 정산 금액 계산기 — 규칙을 한 곳에 격리해 추후 유연하게 조정한다.
 *
 * <ul>
 *   <li>월대 자원: (월대 ÷ {@link #WORKDAYS_PER_MONTH}) × 근무일수 + (OT월단가 × OT일수)</li>
 *   <li>일대 자원: (일대 × 근무일수) + (OT일단가 × OT일수)</li>
 * </ul>
 * 월대·일대·OT 는 서로 독립 단가(변환 없음). 근무일수 미입력(null)이면 금액을 산출하지 않는다(basis 만 노출).
 * 단가는 투입(배차) 시점에 확정된 값을 그대로 받는다.
 */
public final class SettlementCalculator {

    /** 월대를 일 환산할 때 기준 근무일수. ÷30 이 아니다. 규칙 변경 시 여기만 수정. */
    public static final int WORKDAYS_PER_MONTH = 25;

    private SettlementCalculator() {}

    /** amount=null 이면 근무일수 미입력. basis: MONTHLY/DAILY/null(단가 없음). base/ot 는 내역. */
    public record Result(Long amount, String basis, Long baseAmount, Long otAmount) {}

    public static Result calc(Long monthlyPrice, Long dailyPrice,
                              Long otMonthlyPrice, Long otDailyPrice,
                              Integer workDays, Integer otDays) {
        int od = otDays != null ? otDays : 0;
        if (monthlyPrice != null) {
            if (workDays == null) return new Result(null, "MONTHLY", null, null);
            long base = Math.round(monthlyPrice / (double) WORKDAYS_PER_MONTH * workDays);
            long ot = otMonthlyPrice != null ? otMonthlyPrice * od : 0L;
            return new Result(base + ot, "MONTHLY", base, ot);
        }
        if (dailyPrice != null) {
            if (workDays == null) return new Result(null, "DAILY", null, null);
            long base = dailyPrice * workDays;
            long ot = otDailyPrice != null ? otDailyPrice * od : 0L;
            return new Result(base + ot, "DAILY", base, ot);
        }
        return new Result(null, null, null, null);
    }
}
