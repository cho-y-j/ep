package com.skep.quotation.dto;

import com.skep.quotation.QuotationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 현장 묶음 견적 응답 — 사용자 관점에서 "견적 1건" 으로 보이는 단위.
 * 내부적으로 N개의 QuotationRequest 로 저장되지만 같은 bundle_id 로 묶여 한 응답으로 전달.
 *
 * - aggregateStatus: 묶음 전체 진행 상태 (모두 CLOSED → CLOSED, 하나라도 SENT → SENT, 등)
 * - items: 각 자원 명세 (장비/인력) + 그 안의 공급사 target 응답
 */
public record QuotationBundleResponse(
        UUID bundleId,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpCompanyName,
        Long requestedByUserId,
        String requestedByUserName,
        Long onBehalfOfBpCompanyId,
        LocalDate workPeriodStart,
        LocalDate workPeriodEnd,
        String notes,
        QuotationStatus aggregateStatus,
        Integer totalTargets,
        Integer respondedCount,
        Integer acceptedCount,
        Integer finalizedCount,
        /** OPEN_BID 견적의 받은 제안 총 수 (공급사 자유 제출). TARGETED 는 0. */
        Integer proposalCount,
        /** OPEN_BID 중 아직 미선정 (SUBMITTED + PENDING_REVIEW) 제안 수. */
        Integer pendingProposalCount,
        Long firstWorkPlanId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 묶음 안의 각 자원 명세 (기존 QuotationRequestResponse 를 그대로 item 으로 활용). */
        List<QuotationRequestResponse> items
) {}
