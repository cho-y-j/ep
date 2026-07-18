package com.skep.workplan.dto;

import java.util.List;

/**
 * P1c: L2 자원 교체 요청. 최소 하나(장비 또는 조종원)는 지정해야 한다.
 *  - newEquipmentId: 교체할 새 장비 (계획서 1건=장비 1대 정책 — 기존 장비 대체). null 이면 장비 유지.
 *  - newOperatorPersonIds: 교체할 새 조종원 목록. null/빈값이면 조종원 유지(단 장비 교체 시 새 장비로 재매칭).
 *  - reason: 교체 사유(선택).
 */
public record ReplaceResourceRequest(
        Long newEquipmentId,
        List<Long> newOperatorPersonIds,
        String reason
) {
}
