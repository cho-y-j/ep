package com.skep.document;

import com.skep.document.dto.CreateDocumentTypeRequest;
import com.skep.document.dto.DocumentTypeResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    private final DocumentTypeService service;

    public DocumentTypeController(DocumentTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentTypeResponse> list(@RequestParam(required = false) OwnerType appliesTo) {
        List<DocumentType> result = appliesTo != null ? service.listForOwner(appliesTo) : service.listAll();
        return result.stream().map(DocumentTypeResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeResponse create(@Valid @RequestBody CreateDocumentTypeRequest req) {
        return DocumentTypeResponse.from(service.create(req));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentTypeResponse setActive(@PathVariable Long id, @RequestParam boolean active) {
        return DocumentTypeResponse.from(service.activate(id, active));
    }

    // ── V116: '샘플 보기' 예시 이미지 (마스킹된 예시 1장) ──

    /** ADMIN: 마스킹된 예시 업로드(교체). 이미지 1장이면 이미지로, PDF 1개면 PDF로,
     *  2개 이상이면 올린 순서대로 1개 PDF로 병합해 저장. */
    @PostMapping(value = "/{id}/sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentTypeResponse uploadSample(@PathVariable Long id, @RequestParam("file") MultipartFile[] files) {
        return DocumentTypeResponse.from(service.uploadSample(id, files));
    }

    /** ADMIN: 샘플 이미지 삭제. */
    @DeleteMapping("/{id}/sample")
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentTypeResponse deleteSample(@PathVariable Long id) {
        return DocumentTypeResponse.from(service.deleteSample(id));
    }

    /** 공개(무로그인): 마스킹된 예시 이미지 inline 표시 — 수집 대상자가 "이런 걸 올리면 됩니다" 확인용. */
    @GetMapping("/{id}/sample")
    public ResponseEntity<Resource> sample(@PathVariable Long id) {
        DocumentTypeService.SampleImage s = service.loadSample(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(s.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(s.resource());
    }
}
