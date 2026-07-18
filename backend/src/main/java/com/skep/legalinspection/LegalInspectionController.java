package com.skep.legalinspection;

import com.skep.legalinspection.dto.InspectorDtos.BpStatus;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** BP/ADMIN 안전 허브 — 현장 법정점검 현황(완료율·미점검 장비). */
@RestController
@RequestMapping("/api/legal-inspections")
@RequiredArgsConstructor
public class LegalInspectionController {

    private final LegalInspectionService service;

    @GetMapping("/bp-status")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public BpStatus bpStatus(@RequestParam Long siteId, @CurrentUser AuthenticatedUser actor) {
        return service.bpSiteStatus(siteId, actor);
    }
}
