package com.skep.workplan.dto;

import com.skep.equipment.EquipmentCategory;
import com.skep.workplan.WorkPlanEquipment;

import java.time.LocalDateTime;

public record WorkPlanEquipmentResponse(
        Long id,
        Long equipmentId,
        String equipmentName,         // model 또는 vehicle_no
        EquipmentCategory category,
        Long supplierCompanyId,
        String supplierCompanyName,
        String purpose,
        String note,
        Integer dailyRate,
        Integer monthlyRate,
        Long sourceQuotationTargetId,
        LocalDateTime createdAt
) {
    public static WorkPlanEquipmentResponse from(WorkPlanEquipment wpe, String name,
                                                 EquipmentCategory category, String supplierName) {
        return new WorkPlanEquipmentResponse(
                wpe.getId(), wpe.getEquipmentId(), name, category,
                wpe.getSupplierCompanyId(), supplierName,
                wpe.getPurpose(), wpe.getNote(),
                wpe.getDailyRate(), wpe.getMonthlyRate(), wpe.getSourceQuotationTargetId(),
                wpe.getCreatedAt()
        );
    }
}
