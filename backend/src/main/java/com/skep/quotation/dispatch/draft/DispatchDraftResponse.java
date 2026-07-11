package com.skep.quotation.dispatch.draft;

public record DispatchDraftResponse(
        Long id,
        Long quotationRequestId,
        Long supplierCompanyId,
        String supplierCompanyName,
        DispatchDraftResourceType resourceType,
        Long equipmentId,
        String equipmentLabel,
        String equipmentCategory,
        Long personId,
        String personLabel,
        String jobTitle,
        Long dailyPrice,
        Long otDailyPrice,
        Long monthlyPrice,
        Long otMonthlyPrice,
        String notes,
        Long sourceProposalId,
        DispatchDraftStatus status
) {
    public static DispatchDraftResponse from(DispatchDraft d, String supplierName,
                                             String equipmentLabel, String equipmentCategory,
                                             String personLabel, String jobTitle) {
        return new DispatchDraftResponse(
                d.getId(),
                d.getQuotationRequestId(),
                d.getSupplierCompanyId(),
                supplierName,
                d.getResourceType(),
                d.getEquipmentId(),
                equipmentLabel,
                equipmentCategory,
                d.getPersonId(),
                personLabel,
                jobTitle,
                d.getDailyPrice(),
                d.getOtDailyPrice(),
                d.getMonthlyPrice(),
                d.getOtMonthlyPrice(),
                d.getNotes(),
                d.getSourceProposalId(),
                d.getStatus()
        );
    }
}
