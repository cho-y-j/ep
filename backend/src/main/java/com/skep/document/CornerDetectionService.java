package com.skep.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.skep.verify.PaddleOcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 문서 4모서리 자동 검출 — 파일 검증 + 로컬 PaddleOCR(/detect-corners) 호출.
 * 인증 화면({@link DetectCornersController})과 공개 수집 화면(토큰) 컨트롤러가 동일 로직을 공유한다.
 * 응답: {detected, corners:[[x,y]x4], image_size:[w,h]} (paddle 원문). paddle 미가동 시 {detected:false}.
 */
@Service
public class CornerDetectionService {

    private static final Logger log = LoggerFactory.getLogger(CornerDetectionService.class);

    private final PaddleOcrClient paddleClient;

    public CornerDetectionService(PaddleOcrClient paddleClient) {
        this.paddleClient = paddleClient;
    }

    public ResponseEntity<Object> detect(MultipartFile file) throws Exception {
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
