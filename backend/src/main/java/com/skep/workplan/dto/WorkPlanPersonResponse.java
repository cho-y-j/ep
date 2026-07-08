package com.skep.workplan.dto;

import com.skep.workplan.WorkPlanPerson;

import java.time.LocalDateTime;

public record WorkPlanPersonResponse(
        Long id,
        Long personId,
        String personName,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long equipmentId,             // 매칭된 장비 (조종원/신호수). null = 현장 전체
        String role,
        String note,
        LocalDateTime createdAt
) {
    public static WorkPlanPersonResponse from(WorkPlanPerson wpp, String personName, String supplierName) {
        return new WorkPlanPersonResponse(
                wpp.getId(), wpp.getPersonId(), personName,
                wpp.getSupplierCompanyId(), supplierName,
                wpp.getEquipmentId(), wpp.getRole(), wpp.getNote(), wpp.getCreatedAt()
        );
    }
}
