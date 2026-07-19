package com.skep.health;

import com.skep.health.dto.BpCheckinResponse;
import com.skep.health.dto.CreateBpCheckinRequest;
import com.skep.health.dto.SitePersonResponse;
import com.skep.health.dto.UnmeasuredPersonResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * P5-W4 1겹 — 혈압 체크인(관리자·BP·공급사 대행). CLIENT 는 SecurityConfig 전역 차단.
 * 스코프(BP=자기 현장, 공급사=본인 인원)는 서비스에서 재검증.
 */
@RestController
@RequestMapping("/api/bp-checkins")
@RequiredArgsConstructor
public class BpCheckinController {

    private final BpCheckinService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public BpCheckinResponse create(@RequestBody CreateBpCheckinRequest req,
                                    @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    /** 현장·날짜 체크인 목록(date 미지정 시 오늘). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<BpCheckinResponse> list(@RequestParam Long siteId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @CurrentUser AuthenticatedUser actor) {
        return service.listBySiteAndDate(siteId, date, actor);
    }

    /** 현장 배치 인원(작업자 선택 드롭다운). BP=자기 현장, 공급사=본인 인원. */
    @GetMapping("/persons")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<SitePersonResponse> persons(@RequestParam Long siteId,
                                            @CurrentUser AuthenticatedUser actor) {
        return service.sitePersons(siteId, actor);
    }

    /** 오늘(또는 지정일) 혈압 미측정 고위험군(HIGH). */
    @GetMapping("/unmeasured")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<UnmeasuredPersonResponse> unmeasured(@RequestParam Long siteId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                     @CurrentUser AuthenticatedUser actor) {
        return service.unmeasuredHighRisk(siteId, date, actor);
    }
}
