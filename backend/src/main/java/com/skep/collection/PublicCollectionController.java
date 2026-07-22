package com.skep.collection;

import com.skep.collection.dto.CollectionDtos;
import com.skep.document.CornerDetectionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 서류 수집 공개 페이지 — 무로그인, 토큰 기반. SecurityConfig 에서 permitAll. */
@RestController
@RequestMapping("/api/collect")
public class PublicCollectionController {

    private final DocumentCollectionService service;
    private final CornerDetectionService cornerDetection;

    public PublicCollectionController(DocumentCollectionService service, CornerDetectionService cornerDetection) {
        this.service = service;
        this.cornerDetection = cornerDetection;
    }

    @GetMapping("/{token}")
    public CollectionDtos.PublicResponse info(@PathVariable String token) {
        return service.publicGet(token);
    }

    @PostMapping(value = "/{token}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@PathVariable String token,
                                       @RequestParam("itemId") Long itemId,
                                       @RequestParam("file") MultipartFile file) {
        service.publicUpload(token, itemId, file);
        return ResponseEntity.noContent().build();
    }

    /** 등록형 무로그인 등록 — 차량번호/이름 입력 순간 자원(장비/인력) 신규 생성 후 슬롯 연결. body {value}. */
    @PostMapping("/{token}/targets/{targetId}/register")
    public ResponseEntity<Void> register(@PathVariable String token, @PathVariable Long targetId,
                                         @RequestBody(required = false) RegisterRequest req) {
        service.publicRegister(token, targetId, req != null ? req.value() : null);
        return ResponseEntity.noContent().build();
    }

    public record RegisterRequest(String value) {}

    @PostMapping("/{token}/submit")
    public ResponseEntity<Void> submit(@PathVariable String token) {
        service.publicSubmit(token);
        return ResponseEntity.noContent().build();
    }

    /** 무로그인 모서리 자동검출 — 토큰만 검증 후 인증 화면과 동일한 검출 로직 재사용. 응답 형식 동일. */
    @PostMapping(value = "/{token}/detect-corners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> detectCorners(@PathVariable String token,
                                                @RequestParam("file") MultipartFile file) throws Exception {
        service.assertTokenValid(token);
        return cornerDetection.detect(file);
    }
}
