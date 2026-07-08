package com.skep.signature;

import com.skep.docx.WorkPlanPdfService;
import com.skep.signature.dto.SignatureResponse;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 공개 사인 페이지 API — 토큰으로 인증 (비로그인 접근).
 * SecurityConfig 에서 /api/sign/** permitAll.
 */
@RestController
@RequestMapping("/api/sign")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;
    private final WorkPlanRepository workPlanRepo;
    private final WorkPlanPdfService pdfService;

    /** 사인 페이지 진입 — 토큰 검증 + 작업계획서 메타 반환. */
    @GetMapping("/{token}")
    public ResponseEntity<?> getByToken(@PathVariable String token) {
        var sigOpt = signatureService.findByToken(token);
        if (sigOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "유효하지 않은 사인 링크입니다"));
        }
        var sig = sigOpt.get();
        // 만료 토큰은 GET 단계에서 차단 — POST 만 검사하면 만료된 토큰도 조회/메타 노출.
        if (sig.getTokenExpiresAt() != null && java.time.LocalDateTime.now().isAfter(sig.getTokenExpiresAt())) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body(Map.of("error", "만료된 사인 링크입니다"));
        }
        WorkPlan wp = workPlanRepo.findById(sig.getWorkPlanId()).orElse(null);
        String title = wp != null && wp.getTitle() != null ? wp.getTitle() : ("작업계획서 #" + sig.getWorkPlanId());

        return ResponseEntity.ok(Map.of(
                "signature", SignatureResponse.from(sig, SignatureService.roleLabel(sig.getRole()), false),
                "workPlan", Map.of(
                        "id", sig.getWorkPlanId(),
                        "title", title
                )
        ));
    }

    /** 사인 페이지에서 작업계획서 PDF 미리보기/다운로드. 토큰만으로 접근 가능 (비로그인). */
    @GetMapping("/{token}/pdf")
    public ResponseEntity<?> renderPdf(@PathVariable String token,
                                        @RequestParam(name = "disposition", defaultValue = "inline") String disposition) {
        var sigOpt = signatureService.findByToken(token);
        if (sigOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "유효하지 않은 사인 링크입니다"));
        }
        var sig = sigOpt.get();
        if (sig.getTokenExpiresAt() != null && java.time.LocalDateTime.now().isAfter(sig.getTokenExpiresAt())) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "만료된 사인 링크입니다"));
        }
        try {
            byte[] pdf = pdfService.renderPdfPublic(sig.getWorkPlanId());
            WorkPlan wp = workPlanRepo.findById(sig.getWorkPlanId()).orElse(null);
            String baseName = com.skep.common.SafeText.sanitizeFileName(
                    wp != null && wp.getTitle() != null ? wp.getTitle() : "work-plan-" + sig.getWorkPlanId());
            String dispoType = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
            String encodedName = java.net.URLEncoder.encode(baseName + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            dispoType + "; filename*=UTF-8''" + encodedName)
                    .body(new ByteArrayResource(pdf));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "PDF 생성에 실패했습니다"));
        }
    }

    /** PNG 사인 제출. body: { "pngBase64": "iVBOR..." }. 사이즈 cap + magic byte 검증으로 임의 바이너리 차단. */
    @PostMapping("/{token}")
    public ResponseEntity<?> submit(@PathVariable String token, @RequestBody Map<String, String> body) {
        String b64 = body.get("pngBase64");
        if (b64 == null || b64.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "사인 PNG가 비어있습니다"));
        }
        byte[] png;
        try {
            String cleaned = b64.contains(",") ? b64.substring(b64.indexOf(",") + 1) : b64;
            png = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "PNG 디코딩 실패: " + e.getMessage()));
        }
        // 사이즈 cap 2MB — 일반 캔버스 사인은 100KB 미만. 2MB 초과는 페이로드 abuse 의심.
        if (png.length < 8 || png.length > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "PNG 크기가 유효하지 않습니다 (8B~2MB)"));
        }
        // PNG magic byte 검증: 89 50 4E 47 0D 0A 1A 0A
        if (!(png[0] == (byte) 0x89 && png[1] == 0x50 && png[2] == 0x4E && png[3] == 0x47
                && png[4] == 0x0D && png[5] == 0x0A && png[6] == 0x1A && png[7] == 0x0A)) {
            return ResponseEntity.badRequest().body(Map.of("error", "PNG 형식이 아닙니다"));
        }
        try {
            var sig = signatureService.submitSignature(token, png);
            return ResponseEntity.ok(Map.of(
                    "signature", SignatureResponse.from(sig, SignatureService.roleLabel(sig.getRole()), false)
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
