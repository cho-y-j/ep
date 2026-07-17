package com.skep.quotation.dto;

import java.util.List;

/** S-10: 후보 조회 응답 — 공급사별 그룹. */
public record QuotationCandidateResponse(
        Long supplierId,
        String supplierName,
        List<EquipmentItem> equipments
) {
    public record EquipmentItem(
            Long id,
            String vehicleNo,
            String model,
            String manufacturer,
            Integer year,
            String category,
            String serialNumber,
            Boolean hasPhoto,
            Long currentSiteId,
            String currentSiteName,
            Boolean previouslyDispatched,
            Boolean docsReady,
            Integer expiringDocsCount
    ) {}
}
