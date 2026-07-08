package com.skep.equipment.dto;

/** Phase3 장비 투입 통계 1행 — 공급사 장비가 어느 BP 회사 작업계획서에 몇 번 투입됐는지. */
public record EquipmentDeploymentRow(
        Long equipmentId,
        String vehicleNo,
        String model,
        String category,
        boolean external,
        String ownerName,
        Long bpCompanyId,
        String bpCompanyName,
        long deployCount
) {
}
