package com.skep.resourcechange.dto;

import com.skep.resourcechange.ResourceChangeKind;
import com.skep.resourcechange.ResourceChangeRequest;
import com.skep.resourcechange.ResourceChangeStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ResourceChangeRequestResponse(
        Long id,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpName,
        Long supplierCompanyId,
        String supplierName,
        ResourceChangeKind changeKind,
        Long oldEquipmentId,
        Long newEquipmentId,
        Long oldPersonId,
        Long newPersonId,
        String oldLabel,
        String newLabel,
        String oldVehicleNo,
        String newVehicleNo,
        String oldOperatorName,
        String newOperatorName,
        String oldContact,
        String newContact,
        String reason,
        LocalDate applyDate,
        Map<String, Object> l3Snapshot,
        Long workPlanId,
        ResourceChangeStatus status,
        LocalDateTime createdAt
) {
    public static ResourceChangeRequestResponse from(ResourceChangeRequest r, String supplierName) {
        return new ResourceChangeRequestResponse(
                r.getId(), r.getSiteId(), r.getSiteName(),
                r.getBpCompanyId(), r.getBpName(),
                r.getSupplierCompanyId(), supplierName,
                r.getChangeKind(),
                r.getOldEquipmentId(), r.getNewEquipmentId(),
                r.getOldPersonId(), r.getNewPersonId(),
                r.getOldLabel(), r.getNewLabel(),
                r.getOldVehicleNo(), r.getNewVehicleNo(),
                r.getOldOperatorName(), r.getNewOperatorName(),
                r.getOldContact(), r.getNewContact(),
                r.getReason(), r.getApplyDate(),
                r.getL3Snapshot(), r.getWorkPlanId(),
                r.getStatus(), r.getCreatedAt()
        );
    }
}
