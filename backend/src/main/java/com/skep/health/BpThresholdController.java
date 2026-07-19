package com.skep.health;

import com.skep.health.dto.BpThresholdsPayload;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * P5-W4 현장 혈압 임계 설정(자유 설정) — BP(자기 현장)·ADMIN. /api/sites/{siteId}/bp-thresholds
 * (폭염/풍속 완화 금지 가드와 별개 — 안전설정 화면과 나란히 노출용).
 */
@RestController
@RequestMapping("/api/sites/{siteId}/bp-thresholds")
@RequiredArgsConstructor
public class BpThresholdController {

    private final BpCheckinService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public BpThresholdsPayload get(@PathVariable Long siteId, @CurrentUser AuthenticatedUser actor) {
        return service.getThresholds(siteId, actor);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public BpThresholdsPayload save(@PathVariable Long siteId,
                                    @RequestBody BpThresholdsPayload req,
                                    @CurrentUser AuthenticatedUser actor) {
        return service.saveThresholds(siteId, req, actor);
    }
}
