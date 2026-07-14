package com.skep.document.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.skep.verify.PaddleOcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 수퍼어드민 영역지정 도구 전용 — 저장 *전* 초안 템플릿(바디)으로 로컬 PaddleOCR 영역 OCR 미리보기 + warp 이미지 획득.
 * 기존 {@link com.skep.document.OcrRegionPreviewController} 는 DB 에 저장된 doc-type 템플릿을 로드하므로 초안 미리보기 불가 —
 * 이 컨트롤러는 template 을 리터럴로 받아 passthrough 한다. @PreAuthorize·파일검증은 그와 동일.
 *
 * 응답: paddle 원문 {aligned, fields, regions, warped_image_base64?}. paddle 미가동 시 {ok:false}.
 */
@RestController
@RequestMapping("/api/admin/document-types/region-extract")
@PreAuthorize("hasRole('ADMIN')")
public class DocumentTypeRegionExtractController {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeRegionExtractController.class);

    private final PaddleOcrClient paddleClient;

    public DocumentTypeRegionExtractController(PaddleOcrClient paddleClient) {
        this.paddleClient = paddleClient;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> extract(
            @RequestParam("file") MultipartFile file,
            @RequestParam("template") String template,
            @RequestParam(value = "corners", required = false) String corners,
            @RequestParam(value = "returnWarped", required = false, defaultValue = "false") boolean returnWarped
    ) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "파일이 비어있습니다"));
        }
        // 비용 보호 — 파일 크기 cap 10MB
        if (file.getSize() > 10L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "파일이 너무 큽니다 (최대 10MB)"));
        }
        // content-type 화이트리스트 — SVG 차단 (XSS), HEIC 등 미지원 제외.
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        boolean okType = ct.equals("application/pdf")
                || ct.equals("image/jpeg") || ct.equals("image/jpg")
                || ct.equals("image/png") || ct.equals("image/webp") || ct.equals("image/gif");
        if (!okType) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "JPG/PNG/WEBP/GIF/PDF만 가능합니다"));
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        log.info("admin region-extract file={}({} bytes) returnWarped={}", filename, file.getSize(), returnWarped);
        JsonNode result = paddleClient.extractRegionsRaw(file.getBytes(), filename, corners, template, returnWarped);
        if (result == null) {
            return ResponseEntity.ok(Map.of("ok", false));
        }
        return ResponseEntity.ok((Object) result);
    }
}
