package com.skep.quotation.dto;

/**
 * 견적 목록 chip 용 단계 집계.
 *
 * QuotationDetailPage 의 NextStepCard 가 상세에서 3콜(선정/배차/서류묶음)로 판정하던 것을
 * 목록 전체에 대해 서버에서 배치로 계산해 견적당 3단계 완료 여부만 반환.
 *
 * - selected   : target FINAL_ACCEPTED 또는 proposal FINAL_ACCEPTED (공급사 선정 완료)
 * - dispatched : 배차 장비 또는 인원 존재 (차량/인원 단가 발송 수신)
 * - bundle     : 서류 묶음 수신 존재
 */
public record QuotationStageSummaryResponse(
        Long id,
        boolean selected,
        boolean dispatched,
        boolean bundle
) {}
