package com.skep.readiness;

import java.util.List;

/**
 * L3 교체/투입 사전판정 결과. 저장 없음 — 기존 게이트 판정을 자원 단위로 합성한 산출값.
 * ready=true 면 blocks 비어있음. blocks 는 부족 항목 리스트(종류별 kind).
 */
public record DeployCheckResponse(boolean ready, List<DeployBlock> blocks) {

    /** kind: DOCUMENT(서류) | CHECK(반입검사·검진·교육) | SAFETY(안전점검) | COMPLIANCE(이행지시). */
    public record DeployBlock(String kind, String label, String detail) {}
}
