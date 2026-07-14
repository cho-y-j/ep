package com.skep.document;

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
 * 문서 4모서리 자동 검출 프록시 — 폰/스캔 이미지의 문서 경계 4점을 로컬 PaddleOCR(/detect-corners)로 받아
 * FE 정렬 UI(DocumentCornerAligner) 프리필에 쓴다. @PreAuthorize·파일검증은 {@link OcrRegionPreviewController} 와 동일.
 *
 * 응답: {detected, corners:[[x,y]x4], image_size:[w,h]} (paddle 원문). paddle 미가동 시 {detected:false}.
 */
@RestController
@RequestMapping("/api/documents/detect-corners")
public class DetectCornersController {

    private static final Logger log = LoggerFactory.getLogger(DetectCornersController.class);

    private final PaddleOcrClient paddleClient;

    public DetectCornersController(PaddleOcrClient paddleClient) {
        this.paddleClient = paddleClient;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public ResponseEntity<Object> detect(@RequestParam("file") MultipartFile file) throws Exception {
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
        log.info("detect-corners file={}({} bytes)", filename, file.getSize());
        JsonNode result = paddleClient.detectCorners(file.getBytes(), filename);
        if (result == null) {
            return ResponseEntity.ok(Map.of("detected", false));
        }
        return ResponseEntity.ok((Object) result);
    }
}
