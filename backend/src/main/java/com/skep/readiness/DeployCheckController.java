package com.skep.readiness;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L3 교체/투입 사전판정 공개 API — 자원 1건이 (선택 현장 기준) 투입 가능한지.
 * 접근권한은 자원(장비/인원)의 기존 스코프를 그대로 준수(DeployCheckService).
 */
@RestController
@RequiredArgsConstructor
public class DeployCheckController {

    private final DeployCheckService service;

    /** ownerType = equipment | person. siteId 있으면 그 현장 기준 안전점검 판정. */
    @GetMapping("/api/resources/{ownerType}/{ownerId}/deploy-check")
    public DeployCheckResponse deployCheck(@PathVariable String ownerType,
                                           @PathVariable Long ownerId,
                                           @RequestParam(required = false) Long siteId,
                                           @CurrentUser AuthenticatedUser actor) {
        return service.check(ownerType, ownerId, siteId, actor);
    }

    /** R1 조합(차량+조종원) 판정 — 접근권한은 장비 접근권만(장비가 보이면 조합 판정도 보임). 파생, 저장 없음. */
    @GetMapping("/api/resources/equipment/{equipmentId}/deploy-check-combo")
    public ComboDeployCheckResponse deployCheckCombo(@PathVariable Long equipmentId,
                                                     @RequestParam(required = false) Long siteId,
                                                     @CurrentUser AuthenticatedUser actor) {
        return service.checkCombo(equipmentId, siteId, actor);
    }
}
