package com.skep.workconfirmation;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.workconfirmation.dto.MonthlyWorkConfirmationResponse;
import com.skep.workconfirmation.dto.WorkConfirmationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class WorkConfirmationController {

    private final WorkConfirmationService service;

    /** 작업계획서별 작업확인서 목록. */
    @GetMapping("/api/work-plans/{workPlanId}/work-confirmations")
    public List<WorkConfirmationResponse> listByWorkPlan(@PathVariable Long workPlanId,
                                                          @CurrentUser AuthenticatedUser actor) {
        return service.listByWorkPlan(workPlanId, actor).stream()
                .map(wc -> WorkConfirmationResponse.from(wc, false))
                .toList();
    }

    /** 인원 단위 작업확인서 발급. body: { person_id } */
    @PostMapping("/api/work-plans/{workPlanId}/work-confirmations/request")
    public WorkConfirmationResponse request(@PathVariable Long workPlanId,
                                             @RequestBody Map<String, Object> body,
                                             @CurrentUser AuthenticatedUser actor) {
        Object pid = body.get("person_id");
        Long personId = pid instanceof Number n ? n.longValue() : null;
        if (personId == null) {
            throw new IllegalArgumentException("person_id 필수");
        }
        var wc = service.request(workPlanId, personId, actor);
        return WorkConfirmationResponse.from(wc, false);
    }

    @GetMapping("/api/work-confirmations/{id}")
    public WorkConfirmationResponse get(@PathVariable Long id,
                                         @RequestParam(name = "withPng", defaultValue = "false") boolean withPng,
                                         @CurrentUser AuthenticatedUser actor) {
        return WorkConfirmationResponse.from(service.get(id, actor), withPng);
    }

    /** 작업내용 / 시간 등 수정. query param invalidate=true 면 기존 사인 모두 무효화. */
    @PatchMapping(value = "/api/work-confirmations/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkConfirmationResponse update(@PathVariable Long id,
                                            @RequestBody WorkConfirmationService.UpdateRequest req,
                                            @RequestParam(name = "invalidate", defaultValue = "false") boolean invalidate,
                                            @CurrentUser AuthenticatedUser actor) {
        return WorkConfirmationResponse.from(service.update(id, req, invalidate, actor), false);
    }

    /** 공급사측 사인. body: { pngBase64 } — 사이너는 wc.personId 인원 본인 (이미 채워짐). */
    @PostMapping(value = "/api/work-confirmations/{id}/sign-supplier", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkConfirmationResponse signSupplier(@PathVariable Long id,
                                                  @RequestBody Map<String, Object> body,
                                                  @CurrentUser AuthenticatedUser actor) {
        byte[] png = decode(body.get("pngBase64"));
        return WorkConfirmationResponse.from(service.signSupplier(id, png, actor), false);
    }

    /** BP측 사인. body: { pngBase64 } */
    @PostMapping(value = "/api/work-confirmations/{id}/sign-bp", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkConfirmationResponse signBp(@PathVariable Long id,
                                            @RequestBody Map<String, Object> body,
                                            @CurrentUser AuthenticatedUser actor) {
        byte[] png = decode(body.get("pngBase64"));
        return WorkConfirmationResponse.from(service.signBp(id, png, actor), false);
    }

    @PostMapping("/api/work-confirmations/{id}/cancel")
    public WorkConfirmationResponse cancel(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return WorkConfirmationResponse.from(service.cancel(id, actor), false);
    }

    /** 작업확인서 PDF 다운로드 — 사인 PNG 임베드. */
    @GetMapping("/api/work-confirmations/{id}/pdf")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> pdf(
            @PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        byte[] bytes = service.renderPdf(id, actor);
        String fname = java.net.URLEncoder.encode("작업확인서_" + id + ".pdf", java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fname)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new org.springframework.core.io.ByteArrayResource(bytes));
    }

    /** BP 서명 대기 작업확인서 목록 — 공급사 서명완료+BP 미서명. (BP 앱 서명용) */
    @GetMapping("/api/work-confirmations/bp-pending")
    public List<Map<String, Object>> bpPending(@CurrentUser AuthenticatedUser actor) {
        return service.listBpPending(actor);
    }

    /** 공급사 발급 작업확인서 목록 — 서명상태 포함. (공급사 앱 조회용) */
    @GetMapping("/api/work-confirmations/supplier-list")
    public List<Map<String, Object>> supplierList(@CurrentUser AuthenticatedUser actor) {
        return service.listSupplierList(actor);
    }

    /** 월별 작업확인서 집계 — 인원 단위. year/month 필수. 역할별 스코프. */
    @GetMapping("/api/work-confirmations/monthly")
    public List<MonthlyWorkConfirmationResponse> monthly(@RequestParam int year,
                                                         @RequestParam int month,
                                                         @CurrentUser AuthenticatedUser actor) {
        return service.listMonthly(year, month, actor);
    }

    /** 한 인원의 월별 작업확인서 PDF 다운로드. */
    @GetMapping("/api/work-confirmations/monthly/pdf")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.ByteArrayResource> monthlyPdf(
            @RequestParam int year, @RequestParam int month, @RequestParam Long personId,
            @CurrentUser AuthenticatedUser actor) {
        byte[] bytes = service.renderMonthlyPdf(year, month, personId, actor);
        String fname = java.net.URLEncoder.encode("월별작업확인서_" + year + "-" + month + ".pdf",
                java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fname)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new org.springframework.core.io.ByteArrayResource(bytes));
    }

    private static byte[] decode(Object b64Obj) {
        if (!(b64Obj instanceof String b64) || b64.isBlank()) {
            throw new IllegalArgumentException("pngBase64 필수");
        }
        String cleaned = b64.contains(",") ? b64.substring(b64.indexOf(",") + 1) : b64;
        return Base64.getDecoder().decode(cleaned);
    }
}
