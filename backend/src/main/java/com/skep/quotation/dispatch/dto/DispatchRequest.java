package com.skep.quotation.dispatch.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 공급사 견적 응답.
 * 1) 단가 모드 (기본) — items 비우고 top-level dailyPrice/otDailyPrice/monthlyPrice/otMonthlyPrice 입력.
 *    차량 미지정 1행 저장.
 * 2) 차량 지정 모드 (구버전 호환) — items 에 차량별 단가 입력. 차량 1대당 1행.
 */
public record DispatchRequest(
        List<Item> items,
        Long dailyPrice,
        Long otDailyPrice,
        Long monthlyPrice,
        Long otMonthlyPrice,
        String notes,
        String dailyNote,
        String otDailyNote,
        String monthlyNote,
        String otMonthlyNote
) {
    public record Item(
            @NotNull Long equipmentId,
            Long dailyPrice,
            Long otDailyPrice,
            Long monthlyPrice,
            Long otMonthlyPrice,
            String notes,
            String dailyNote,
            String otDailyNote,
            String monthlyNote,
            String otMonthlyNote
    ) {}
}
