package com.skep.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.verify.VerifyClient;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * S-9-G.2: OCR 프리뷰 — 파일을 업로드 *전에* verify-api 의 OCR 만 돌려 추출 결과를 미리 받음.
 * 폼이 좌측 미리보기 + 우측 자동채움 입력으로 동작하도록.
 *
 * 응답: extract type 별 키 (BUSINESS_REGISTRATION 의 경우 businessNumber/representativeName/businessName/startDate/address/businessType).
 * verify-api 미가동 시 빈 객체 + reasonCode.
 */
@RestController
@RequestMapping("/api/documents/ocr-preview")
public class OcrPreviewController {

    private static final Logger log = LoggerFactory.getLogger(OcrPreviewController.class);

    private final VerifyClient verifyClient;

    public OcrPreviewController(VerifyClient verifyClient) {
        this.verifyClient = verifyClient;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ocrType") String ocrType,
            @CurrentUser AuthenticatedUser actor
    ) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "파일이 비어있습니다"));
        }
        // verify-api 비용 보호 — 파일 크기 cap 10MB
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
        log.info("OCR preview by user={} ocrType={} file={}({} bytes)",
                actor.id(), ocrType, file.getOriginalFilename(), file.getSize());

        JsonNode raw = verifyClient.extractOcr(ocrType, file.getBytes(),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg");

        Map<String, Object> result = new HashMap<>();
        result.put("ok", raw != null && !raw.isEmpty() && !raw.has("reasonCode"));
        if (raw != null) {
            JsonNode fields = raw.has("fields") ? raw.get("fields") : raw;
            Map<String, String> flat = flatten(fields);
            // 주민번호 검출 시 verify-api 가 마스킹된 이미지를 함께 반환 — fields 에서 분리해 별도 키로.
            String maskedImage = flat.remove("maskedImageBase64");
            result.put("fields", flat);
            if (maskedImage != null && !maskedImage.isEmpty()) {
                result.put("masked_image_base64", maskedImage);
            }
            if (raw.has("reasonCode")) {
                result.put("reasonCode", raw.get("reasonCode").asText());
                result.put("message", raw.has("message") ? raw.get("message").asText() : "OCR 호출 실패");
            }
        }
        return ResponseEntity.ok(result);
    }

    /** JsonNode object → flat string map (객체 안 1단계만). */
    private static Map<String, String> flatten(JsonNode node) {
        Map<String, String> map = new HashMap<>();
        if (node == null || !node.isObject()) return map;
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v.isTextual() || v.isNumber() || v.isBoolean()) {
                map.put(e.getKey(), v.asText());
            }
        }
        return map;
    }
}
