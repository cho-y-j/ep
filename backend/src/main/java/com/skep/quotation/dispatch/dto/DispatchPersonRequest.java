package com.skep.quotation.dispatch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 선정 통보 받은 공급사가 인원 다중 선택 후 보내기 (차량과 함께).
 * notes: 자유 텍스트.
 */
public record DispatchPersonRequest(
        @NotEmpty List<Item> items,
        String notes
) {
    public record Item(
            @NotNull Long personId,
            Long dailyPrice,
            Long otDailyPrice,
            Long monthlyPrice,
            Long otMonthlyPrice,
            String notes
    ) {}
}
