package com.skep.contract.dto;

import com.skep.contract.Contract;
import com.skep.contract.RateType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractResponse(
        Long id,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long bpCompanyId,
        String bpCompanyName,
        String bpName,
        Long siteId,
        String siteName,
        String title,
        String equipmentDesc,
        RateType rateType,
        Long baseRate,
        Long rateEarly,
        Long rateLunch,
        Long rateEvening,
        Long rateNight,
        Long rateOvernight,
        LocalDate startDate,
        LocalDate endDate,
        boolean hasFile,
        String memo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContractResponse from(Contract c, String supplierName, String bpCompanyName) {
        return new ContractResponse(
                c.getId(), c.getSupplierCompanyId(), supplierName,
                c.getBpCompanyId(), bpCompanyName, c.getBpName(),
                c.getSiteId(), c.getSiteName(),
                c.getTitle(), c.getEquipmentDesc(),
                c.getRateType(), c.getBaseRate(),
                c.getRateEarly(), c.getRateLunch(), c.getRateEvening(),
                c.getRateNight(), c.getRateOvernight(),
                c.getStartDate(), c.getEndDate(),
                c.getFileKey() != null, c.getMemo(),
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
