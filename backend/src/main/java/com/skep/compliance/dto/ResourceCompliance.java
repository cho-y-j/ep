package com.skep.compliance.dto;

import com.skep.document.OwnerType;

import java.util.List;

/** 단일 자원 (장비/인원/회사) 의 서류 컴플라이언스 표. */
public record ResourceCompliance(
        OwnerType ownerType,
        Long ownerId,
        String ownerName,
        String ownerSubLabel,        // 카테고리/역할 등 부가 정보
        Long supplierCompanyId,
        String supplierCompanyName,
        List<ComplianceItem> items,
        int requiredTotal,
        int requiredOk,
        int missingCount,
        int rejectedCount,
        int expiringCount,
        int openSupplementCount,
        boolean readyForWorkPlan      // 모든 required 가 OK 이고 공급중 보완요청 없음
) {}
