package com.skep.document;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 4모서리 자동 검출 프록시 — 폰/스캔 이미지의 문서 경계 4점을 {@link CornerDetectionService} 로 받아
 * FE 정렬 UI(DocumentCornerAligner) 프리필에 쓴다. 인증 필수(로그인 화면 전용).
 * 공개(무로그인) 수집 화면은 토큰 기반 {@code POST /api/collect/{token}/detect-corners} 를 쓴다.
 */
@RestController
@RequestMapping("/api/documents/detect-corners")
public class DetectCornersController {

    private final CornerDetectionService detection;

    public DetectCornersController(CornerDetectionService detection) {
        this.detection = detection;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public ResponseEntity<Object> detect(@RequestParam("file") MultipartFile file) throws Exception {
        return detection.detect(file);
    }
}
