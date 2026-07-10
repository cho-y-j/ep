package com.skep.signature;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.signature.dto.SignatureResponse;
import com.skep.user.Role;
import com.skep.workplan.WorkPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 작업계획서 사인 관리 API — 인증된 BP 사용자 (작업계획서 소유자) 호출.
 *
 * - GET    /signatures                    : 5개 사인 슬롯 상태 조회
 * - POST   /signatures/author             : 작성자 본인 사인 (PNG 파일)
 * - POST   /signatures/request            : 4명 외부 사인 요청 (이메일 발송)
 * - POST   /signatures/invalidate         : 워크시트 수정 시 모든 사인 무효화
 */
@RestController
@RequestMapping("/api/work-plans/{workPlanId}/signatures")
@RequiredArgsConstructor
public class WorkPlanSignatureController {

    private final SignatureService signatureService;
    private final WorkPlanService workPlanService;

    @GetMapping
    public List<SignatureResponse> list(@PathVariable Long workPlanId,
                                         @RequestParam(name = "withPng", defaultValue = "false") boolean withPng,
                                         @CurrentUser AuthenticatedUser actor) {
        // P1: 작업계획서 조회 권한 통과해야 사인 목록도 접근 가능. withPng=true 면 PNG 까지.
        var wp = workPlanService.get(workPlanId, actor);
        // 사인 PNG(5슬롯 모두 BP/원청 인물)는 ADMIN 또는 해당 작업계획서 BP 본인만 열람. 공급사는 메타만.
        boolean pngAllowed = withPng && (actor.role() == Role.ADMIN
                || (actor.companyId() != null && actor.companyId().equals(wp.bpCompanyId())));
        org.slf4j.LoggerFactory.getLogger(WorkPlanSignatureController.class)
                .info("[SIG-LIST] workPlanId={} withPng={} pngAllowed={}", workPlanId, withPng, pngAllowed);
        return signatureService.listForWorkPlan(workPlanId).stream()
                .map(s -> {
                    byte[] existing = s.getSignaturePng();
                    org.slf4j.LoggerFactory.getLogger(WorkPlanSignatureController.class)
                            .info("[SIG-LIST] role={} status={} entityPngLen={}",
                                    s.getRole(), s.getStatus(), existing == null ? -1 : existing.length);
                    if (pngAllowed && s.getStatus() == SignatureStatus.SIGNED
                            && (existing == null || existing.length == 0)) {
                        byte[] png = signatureService.fetchPngById(s.getId());
                        if (png != null && png.length > 0) s.setSignaturePng(png);
                    }
                    return SignatureResponse.from(s, SignatureService.roleLabel(s.getRole()), pngAllowed);
                })
                .toList();
    }

    /** 작성자 본인 사인 — PNG base64 (캔버스 dataURL). */
    @PostMapping(value = "/author", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SignatureResponse signAsAuthorJson(@PathVariable Long workPlanId,
                                               @RequestBody Map<String, String> body,
                                               @CurrentUser AuthenticatedUser actor) {
        String b64 = body.get("pngBase64");
        if (b64 == null || b64.isBlank()) {
            throw new IllegalArgumentException("pngBase64 필드가 필요합니다");
        }
        String cleaned = b64.contains(",") ? b64.substring(b64.indexOf(",") + 1) : b64;
        byte[] png = Base64.getDecoder().decode(cleaned);
        // 공개 제출 경로(SignatureController#submit)와 동일한 크기 cap + PNG 매직바이트 검증.
        if (png.length < 8 || png.length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("PNG 크기가 유효하지 않습니다 (8B~2MB)");
        }
        if (!(png[0] == (byte) 0x89 && png[1] == 0x50 && png[2] == 0x4E && png[3] == 0x47
                && png[4] == 0x0D && png[5] == 0x0A && png[6] == 0x1A && png[7] == 0x0A)) {
            throw new IllegalArgumentException("PNG 형식이 아닙니다");
        }
        var sig = signatureService.signAsAuthor(workPlanId, png, actor);
        return SignatureResponse.from(sig, SignatureService.roleLabel(sig.getRole()), false);
    }

    /**
     * 4명 사인 요청 — body: { "SUPERVISOR": {name, email}, "CONFIRMER": {...}, ... }
     *
     * @param attachPdf  메일에 작업계획서 PDF 첨부 여부 (기본 true).
     * @param templateId PDF 변환 시 사용할 DOCX 템플릿 id (선택). 미지정시 시스템 첫번째 가시 템플릿 자동 선택.
     */
    @PostMapping("/request")
    public List<SignatureResponse> requestExternal(@PathVariable Long workPlanId,
                                                    @RequestBody Map<String, Map<String, String>> body,
                                                    @RequestParam(name = "attachPdf", defaultValue = "false") boolean attachPdf,
                                                    @RequestParam(name = "templateId", required = false) Long templateId,
                                                    @CurrentUser AuthenticatedUser actor) {
        EnumMap<SignatureRole, SignatureService.RequestSpec> specs = new EnumMap<>(SignatureRole.class);
        for (var e : body.entrySet()) {
            SignatureRole role;
            try { role = SignatureRole.valueOf(e.getKey()); } catch (IllegalArgumentException ex) { continue; }
            if (role == SignatureRole.AUTHOR) continue;
            Map<String, String> spec = e.getValue();
            if (spec == null) continue;
            String name = spec.get("name");
            String email = spec.get("email");
            if (email == null || email.isBlank()) continue;
            specs.put(role, new SignatureService.RequestSpec(name, email));
        }
        var result = signatureService.requestExternalSigns(workPlanId, specs, actor, templateId, attachPdf);
        return result.values().stream()
                .map(s -> SignatureResponse.from(s, SignatureService.roleLabel(s.getRole()), false))
                .toList();
    }

    /** 워크시트 수정 시 모든 사인 무효화. */
    @PostMapping("/invalidate")
    public ResponseEntity<Map<String, Object>> invalidate(@PathVariable Long workPlanId,
                                                           @CurrentUser AuthenticatedUser actor) {
        int count = signatureService.invalidateAll(workPlanId, actor);
        return ResponseEntity.ok(Map.of("invalidated", count));
    }

    @ExceptionHandler({ SecurityException.class })
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
