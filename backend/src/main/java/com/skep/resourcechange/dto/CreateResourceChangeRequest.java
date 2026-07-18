package com.skep.resourcechange.dto;

import com.skep.resourcechange.ResourceChangeKind;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 업체변경 신청서 v0 생성 요청. 변경 전/후 자원 id 는 변경구분에 맞는 것만 채운다.
 *  - 공급사 로그인: supplierCompanyId 무시(본인 회사). BP 로그인: bp=본인, supplierCompanyId 필수.
 *  - 라벨/차량번호/조종원명/연락처 스냅샷과 신규자원 L3(deploy-check) 스냅샷은 서버가 생성.
 */
public record CreateResourceChangeRequest(
        @NotNull ResourceChangeKind changeKind,
        Long siteId,
        String siteName,
        Long bpCompanyId,
        Long supplierCompanyId,
        Long oldEquipmentId,
        Long newEquipmentId,
        Long oldPersonId,
        Long newPersonId,
        String reason,
        LocalDate applyDate,
        Long workPlanId
) {}
