package com.skep.collection;

import com.skep.collection.dto.CollectionDtos;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 서류 수집 공개 페이지 — 무로그인, 토큰 기반. SecurityConfig 에서 permitAll. */
@RestController
@RequestMapping("/api/collect")
public class PublicCollectionController {

    private final DocumentCollectionService service;

    public PublicCollectionController(DocumentCollectionService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public CollectionDtos.PublicResponse info(@PathVariable String token) {
        return service.publicGet(token);
    }

    @PostMapping(value = "/{token}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@PathVariable String token,
                                       @RequestParam("documentTypeId") Long documentTypeId,
                                       @RequestParam("file") MultipartFile file) {
        service.publicUpload(token, documentTypeId, file);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<Void> submit(@PathVariable String token) {
        service.publicSubmit(token);
        return ResponseEntity.noContent().build();
    }
}
