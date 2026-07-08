package com.skep.quotation.dto;

import com.skep.person.PersonRole;

import java.util.List;
import java.util.Set;

/** 인력 견적 후보 — 인력공급사별 그룹 (해당 역할 가능 인원 포함). */
public record QuotationManpowerCandidateResponse(
        Long supplierId,
        String supplierName,
        List<PersonItem> persons
) {
    public record PersonItem(
            Long id,
            String name,
            String jobTitle,
            String phone,
            String employeeNo,
            Set<PersonRole> roles,
            Boolean hasPhoto
    ) {}
}
