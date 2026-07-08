package com.skep.compliance.dto;

import java.util.List;

/** 사이트 단위 통합 컴플라이언스 — 작업계획서 만들 준비 게이트. */
public record SiteCompliance(
        Long siteId,
        String siteName,
        Long bpCompanyId,
        String bpCompanyName,
        ResourceCompliance bpCompany,         // BP 회사 자체 서류 (사업자등록증 등)
        List<ResourceCompliance> equipments,   // 사이트 ACTIVE EQUIPMENT_SUPPLIER 의 자원들
        List<ResourceCompliance> persons,      // 사이트 ACTIVE 공급사 인원들
        int totalRequiredItems,
        int totalOkItems,
        int progressPct,
        boolean readyForWorkPlan
) {}
