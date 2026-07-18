package com.skep.dailywork.dto;

import com.skep.contract.RateType;
import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.WorkLogSignStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record DailyWorkLogResponse(
        Long id,
        Long supplierCompanyId,
        String supplierCompanyName,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpCompanyName,
        Long contractId,
        Long equipmentId,
        String equipmentLabel,
        Long personId,
        String personName,
        LocalDate workDate,
        String workContent,
        String workLocation,
        RateType rateType,
        BigDecimal otEarly,
        BigDecimal otLunch,
        BigDecimal otEvening,
        BigDecimal otNight,
        BigDecimal otOvernight,
        LocalTime startTime,
        LocalTime endTime,
        String memo,
        WorkLogSignStatus signStatus,
        LocalDateTime bpSignedAt,
        boolean hasSignImage,
        boolean hasSlipPhoto,
        LocalDateTime createdAt
) {
    public static DailyWorkLogResponse from(DailyWorkLog l, String supplierName, String bpName,
                                            String equipmentLabel, String personName) {
        return new DailyWorkLogResponse(
                l.getId(), l.getSupplierCompanyId(), supplierName,
                l.getSiteId(), l.getSiteName(),
                l.getBpCompanyId(), bpName,
                l.getContractId(),
                l.getEquipmentId(), equipmentLabel,
                l.getPersonId(), personName,
                l.getWorkDate(), l.getWorkContent(), l.getWorkLocation(),
                l.getRateType(),
                l.getOtEarly(), l.getOtLunch(), l.getOtEvening(), l.getOtNight(), l.getOtOvernight(),
                l.getStartTime(), l.getEndTime(), l.getMemo(),
                l.getSignStatus(), l.getBpSignedAt(),
                l.getSignImage() != null, l.getSlipPhotoKey() != null,
                l.getCreatedAt()
        );
    }
}
