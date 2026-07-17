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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 영역-크롭 OCR 프리뷰 — 업로드 *전에* 로컬 PaddleOCR 로 doc-type 의 영역맵(ocr_region_template)
 * 기준으로 필드를 추출해 폼 자동채움. Vision 경로({@link OcrPreviewController})와 병렬 — 템플릿 보유
 * doc-type 에만 FE 가 분기 호출한다.
 *
 * 응답: {ok, fields} 또는 템플릿 없음 시 {ok:false, reasonCode:"NO_TEMPLATE"}.
 */
@RestController
@RequestMapping("/api/documents/ocr-region-preview")
public class OcrRegionPreviewController {

    private static final Logger log = LoggerFactory.getLogger(OcrRegionPreviewController.class);

    private final DocumentTypeRepository typeRepo;
    private final PaddleOcrClient paddleClient;

    public OcrRegionPreviewController(DocumentTypeRepository typeRepo, PaddleOcrClient paddleClient) {
        this.typeRepo = typeRepo;
        this.paddleClient = paddleClient;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentTypeId") Long documentTypeId,
            @RequestParam(value = "corners", required = false) String corners
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

        DocumentType type = typeRepo.findById(documentTypeId).orElse(null);
        String template = type != null ? type.getOcrRegionTemplate() : null;
        if (template == null || template.isBlank()) {
            return ResponseEntity.ok(Map.of("ok", false, "reasonCode", "NO_TEMPLATE"));
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
        boolean hasCorners = corners != null && !corners.isBlank();
        log.info("OCR region preview documentTypeId={} file={}({} bytes) corners={}",
                documentTypeId, filename, file.getSize(), hasCorners);

        Map<String, String> fields;
        String warpedBase64 = null;
        if (hasCorners) {
            // 4모서리 정렬 경로 — warp(원근보정+크롭)된 이미지도 받아 FE 가 "맞춘 이미지" 미리보기로 보여준다.
            JsonNode raw = paddleClient.extractRegionsRaw(file.getBytes(), filename, corners, template, true);
            fields = parseFields(raw);
            if (raw != null) {
                JsonNode w = raw.get("warped_image_base64");
                if (w != null && !w.isNull()) warpedBase64 = w.asText();
            }
        } else {
            fields = paddleClient.extractRegions(file.getBytes(), filename, corners, template);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", fields != null && !fields.isEmpty());
        result.put("fields", fields != null ? fields : Map.of());
        if (warpedBase64 != null) result.put("warped_image_base64", warpedBase64);
        return ResponseEntity.ok(result);
    }

    /** raw /extract-regions 응답의 fields 오브젝트 → Map(null 값 제외). raw null 이면 null. */
    private static Map<String, String> parseFields(JsonNode raw) {
        if (raw == null) return null;
        JsonNode fields = raw.get("fields");
        if (fields == null || !fields.isObject()) return null;
        Map<String, String> map = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode v = e.getValue();
            if (v != null && !v.isNull()) map.put(e.getKey(), v.asText());
        }
        return map;
    }
}
