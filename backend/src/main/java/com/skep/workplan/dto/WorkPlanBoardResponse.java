package com.skep.workplan.dto;

import com.skep.workplan.WorkPlanStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * BP 현장 보드(read-only) — 계획서 1건 + 배치(장비/인원) 최소 필드.
 * 단가·게이트·컴플라이언스 미포함(표시 전용). 목록 API 가 summary 만 주기 때문에
 * 보드가 계획서별 상세 N+1 호출 없이 세트(장비+조종원+유도원 등)를 조립하는 데 쓴다.
 */
public record WorkPlanBoardResponse(
        Long id,
        Long siteId,
        String siteName,
        WorkPlanStatus status,
        LocalDate workDate,
        String title,
        List<BoardEquipment> equipment,
        List<BoardPerson> persons
) {
    public record BoardEquipment(
            Long equipmentId,
            String label,
            Long supplierCompanyId,
            String supplierCompanyName
    ) {}

    public record BoardPerson(
            Long personId,
            String name,
            String role,
            Long equipmentId,          // 매칭된 장비 (조종원/유도원 등). null = 현장 전체
            Long supplierCompanyId,
            String supplierCompanyName
    ) {}
}
