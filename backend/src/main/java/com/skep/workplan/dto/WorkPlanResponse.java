package com.skep.workplan.dto;

import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public record WorkPlanResponse(
        Long id,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpCompanyName,
        LocalDate workDate,
        LocalTime startTime,
        LocalTime endTime,
        String title,
        String workLocation,
        String description,
        WorkPlanStatus status,
        Long createdBy,
        LocalDateTime submittedAt,
        Long submittedBy,
        LocalDateTime approvedAt,
        Long approvedBy,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // P1c: L2 교체로 대체 생성된 계획서면 원본 id (이력 연결). 아니면 null.
        Long clonedFromId,
        // 상세 응답에서만 채움 (목록은 null)
        List<WorkPlanEquipmentResponse> equipment,
        List<WorkPlanPersonResponse> persons,
        List<ComplianceCheckResponse> complianceChecks,
        // P1a 기반①: 워크시트 폼 상태 (132 필드 + roleAssign + 첨부 선택). 상세만, 목록/요약은 null.
        Map<String, Object> formValues
) {
    public static WorkPlanResponse summary(WorkPlan wp, String siteName, String bpCompanyName) {
        return new WorkPlanResponse(
                wp.getId(), wp.getSiteId(), siteName, wp.getBpCompanyId(), bpCompanyName,
                wp.getWorkDate(), wp.getStartTime(), wp.getEndTime(),
                wp.getTitle(), wp.getWorkLocation(), wp.getDescription(),
                wp.getStatus(), wp.getCreatedBy(),
                wp.getSubmittedAt(), wp.getSubmittedBy(),
                wp.getApprovedAt(), wp.getApprovedBy(),
                wp.getCancelledAt(), wp.getCancelReason(),
                wp.getCreatedAt(), wp.getUpdatedAt(),
                wp.getClonedFromId(),
                null, null, null, null
        );
    }

    public static WorkPlanResponse detail(WorkPlan wp, String siteName, String bpCompanyName,
                                          List<WorkPlanEquipmentResponse> equipment,
                                          List<WorkPlanPersonResponse> persons,
                                          List<ComplianceCheckResponse> complianceChecks) {
        return new WorkPlanResponse(
                wp.getId(), wp.getSiteId(), siteName, wp.getBpCompanyId(), bpCompanyName,
                wp.getWorkDate(), wp.getStartTime(), wp.getEndTime(),
                wp.getTitle(), wp.getWorkLocation(), wp.getDescription(),
                wp.getStatus(), wp.getCreatedBy(),
                wp.getSubmittedAt(), wp.getSubmittedBy(),
                wp.getApprovedAt(), wp.getApprovedBy(),
                wp.getCancelledAt(), wp.getCancelReason(),
                wp.getCreatedAt(), wp.getUpdatedAt(),
                wp.getClonedFromId(),
                equipment, persons, complianceChecks, wp.getFormValues()
        );
    }
}
