package com.skep.safety;

import com.skep.safety.dto.CreateInspectionRequest;
import com.skep.safety.dto.InspectionResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/safety-inspections")
@RequiredArgsConstructor
public class SafetyInspectionController {

    private final SafetyInspectionService service;

    /** BP/ADMIN — 안전점검 일정 등록. */
    @PostMapping
    public InspectionResponse create(@Valid @RequestBody CreateInspectionRequest req, @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    /** BP/ADMIN — 공급사에 통보. 1회 멱등 (이미 SENT 면 IllegalStateException → 400). */
    @PostMapping("/{id}/send")
    public InspectionResponse send(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.sendToSupplier(id, actor);
    }

    /** 공급사 — 일정 확인. */
    @PostMapping("/{id}/confirm")
    public InspectionResponse confirm(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.confirm(id, actor);
    }

    /** BP/ADMIN — 검사 완료 처리. */
    @PostMapping("/{id}/complete")
    public InspectionResponse complete(@PathVariable Long id,
                                       @RequestBody(required = false) java.util.Map<String, String> body,
                                       @CurrentUser AuthenticatedUser actor) {
        String notes = body != null ? body.getOrDefault("resultNotes", null) : null;
        return service.complete(id, notes, actor);
    }

    /** 현장별 list. BP/ADMIN. */
    @GetMapping("/site/{siteId}")
    public List<InspectionResponse> listBySite(@PathVariable Long siteId, @CurrentUser AuthenticatedUser actor) {
        return service.listBySite(siteId, actor);
    }

    /** B5: target(장비/인원) 기준 검사 상태 — BP 본인 현장만. A2 연결뷰 병기용. */
    @GetMapping("/by-target")
    public List<InspectionResponse> listByTarget(@RequestParam InspectionTarget targetType,
                                                 @RequestParam Long targetId,
                                                 @CurrentUser AuthenticatedUser actor) {
        return service.listByTargetForBp(targetType, targetId, actor);
    }

    /** 공급사 — 자기 회사 받은 검사 list. */
    @GetMapping("/mine")
    public List<InspectionResponse> listMine(@CurrentUser AuthenticatedUser actor) {
        return service.listMine(actor);
    }
}
