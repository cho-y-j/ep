package com.skep.safety;

import com.skep.safety.dto.SafetyReportDtos.SafetyReport;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * P3d 안전관리 이행 보고서 — 현장·기간별 증거사슬 집계(감사·사고 조사 제출용).
 * ADMIN·BP·CLIENT 접근(SecurityConfig 매처 + 서비스 현장 스코프 재검증). 공급사/작업자 403.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','BP','CLIENT')")
public class SafetyReportController {

    private final SafetyReportService service;

    public SafetyReportController(SafetyReportService service) {
        this.service = service;
    }

    @GetMapping("/api/safety-reports")
    public SafetyReport report(
            @RequestParam Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @CurrentUser AuthenticatedUser actor) {
        return service.report(siteId, from, to, actor);
    }
}
