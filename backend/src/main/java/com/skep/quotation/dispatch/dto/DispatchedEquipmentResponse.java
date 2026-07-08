package com.skep.quotation.dispatch.dto;

import com.skep.quotation.dispatch.DispatchedEquipment;

import java.time.LocalDateTime;

public record DispatchedEquipmentResponse(
        Long id,
        Long quotationRequestId,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long equipmentId,
        String equipmentLabel,
        String equipmentCategory,
        Long dailyPrice,
        Long otDailyPrice,
        Long monthlyPrice,
        Long otMonthlyPrice,
        String notes,
        String dailyNote,
        String otDailyNote,
        String monthlyNote,
        String otMonthlyNote,
        LocalDateTime sentAt
) {
    public static DispatchedEquipmentResponse from(DispatchedEquipment d, String supplierName, String equipmentLabel, String category) {
        return new DispatchedEquipmentResponse(
                d.getId(),
                d.getQuotationRequestId(),
                d.getSupplierCompanyId(),
                supplierName,
                d.getEquipmentId(),
                equipmentLabel,
                category,
                d.getDailyPrice(),
                d.getOtDailyPrice(),
                d.getMonthlyPrice(),
                d.getOtMonthlyPrice(),
                d.getNotes(),
                d.getDailyNote(),
                d.getOtDailyNote(),
                d.getMonthlyNote(),
                d.getOtMonthlyNote(),
                d.getSentAt()
        );
    }
}
