package com.skep.assignment.dto;

import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentAssignmentStatus;
import com.skep.equipment.EquipmentCategory;

import java.time.LocalDateTime;

/**
 * 작업계획서/현장 배치 후보 장비. 단순 장비 목록 + 후보 추천을 위한 메타데이터.
 *
 * - previously_used_on_site : 이 자원이 해당 현장에 이전 배치된 적 있는지 (이력 기준)
 * - currently_assigned       : 현재 다른 현장에 배치 중인지
 * - missing_documents        : 필수 서류 누락 수 (Phase 3에서 채움 — 이번 단계는 0 고정)
 * - expiring_documents       : 만료 임박 서류 수 (V8/V9 의 expiring_count 재사용)
 * - blocked                  : 사용 제한 대상 (BROKEN 등)
 */
public record EquipmentCandidateResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String name,            // model 또는 vehicle_no
        EquipmentCategory category,
        String code,
        String vehicleNo,
        boolean hasPhoto,
        EquipmentAssignmentStatus assignmentStatus,
        Long currentSiteId,
        String currentSiteName,
        LocalDateTime lastAssignedAt,
        boolean previouslyUsedOnSite,
        boolean currentlyAssigned,
        long expiringDocuments,
        long missingDocuments,
        boolean blocked
) {
    public static EquipmentCandidateResponse from(
            Equipment e,
            String supplierName,
            String currentSiteName,
            boolean previouslyUsedOnSite,
            long expiringDocuments,
            long missingDocuments
    ) {
        boolean currentlyAssigned = e.getCurrentSiteId() != null
                && e.getAssignmentStatus() == EquipmentAssignmentStatus.ASSIGNED;
        boolean blocked = e.getAssignmentStatus() == EquipmentAssignmentStatus.BROKEN
                || missingDocuments > 0;
        String name = e.getModel() != null && !e.getModel().isBlank()
                ? e.getModel()
                : (e.getVehicleNo() != null ? e.getVehicleNo() : "(이름 없음)");
        return new EquipmentCandidateResponse(
                e.getId(),
                e.getSupplierId(),
                supplierName,
                name,
                e.getCategory(),
                e.getCode(),
                e.getVehicleNo(),
                e.getPhotoKey() != null,
                e.getAssignmentStatus(),
                e.getCurrentSiteId(),
                currentSiteName,
                e.getLastAssignedAt(),
                previouslyUsedOnSite,
                currentlyAssigned,
                expiringDocuments,
                missingDocuments,
                blocked
        );
    }
}
