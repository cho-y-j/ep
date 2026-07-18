package com.skep.safety;

import com.skep.safety.dto.SiteSafetySettingsRequest;
import com.skep.safety.dto.SiteSafetySettingsResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 현장 안전설정(§3.4) — BP(자기 현장)·ADMIN. /api/sites/{siteId}/safety-settings */
@RestController
@RequestMapping("/api/sites/{siteId}/safety-settings")
@RequiredArgsConstructor
public class SiteSafetySettingsController {

    private final SiteSafetySettingsService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public SiteSafetySettingsResponse get(@PathVariable Long siteId, @CurrentUser AuthenticatedUser actor) {
        return service.get(siteId, actor);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public SiteSafetySettingsResponse save(@PathVariable Long siteId,
                                           @RequestBody SiteSafetySettingsRequest req,
                                           @CurrentUser AuthenticatedUser actor) {
        return service.save(siteId, req, actor);
    }
}
