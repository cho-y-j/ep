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
                                       // 협력사 만료일 입력(선택) — 미입력/형식오류는 null 저장(fail-open, 관리자가 채움).
                                       @RequestParam(value = "expiryDate", required = false) String expiryDate,
                                       // 파일 1개면 그대로, 2개 이상이면 올린 순서대로 1개 PDF로 병합 후 저장.
                                       @RequestParam("file") MultipartFile[] files) {
        service.publicUpload(token, itemId, files, parseDate(expiryDate));
        return ResponseEntity.noContent().build();
    }

    /** ISO(yyyy-MM-dd) 파싱. 실패 시 null — 무로그인 업로드를 절대 막지 않는다. */
    private static java.time.LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(s);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
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
