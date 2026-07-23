package com.skep.readiness;

import com.skep.readiness.DeployCheckResponse.DeployBlock;

import java.util.List;

/**
 * R1 조합(차량+조종원) 판정 결과 — 저장 없음. 기존 4게이트(DeployCheckService)를
 * 장비 1회 + 조합(교대조) 조종원 N회 산출해 합성한 파생값.
 * combo_ready = 장비 ready AND 조종원 전원 ready. 조종원 0명이면 장비 단독 판정(operators 빈 리스트).
 */
public record ComboDeployCheckResponse(
        Long equipmentId,
        String equipmentLabel,
        boolean comboReady,
        DeployCheckResponse equipment,
        List<OperatorCheck> operators) {

    /** priority = 교대 순번(1부터, equipment_default_operators.priority — operator_person_id 단일 폴백이면 1). */
    public record OperatorCheck(Long personId, String personName, int priority,
                                boolean ready, List<DeployBlock> blocks) {}
}
