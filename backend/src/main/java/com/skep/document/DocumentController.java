package com.skep.document;

import com.skep.document.dto.DocumentResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentResponse> list(
            @RequestParam OwnerType ownerType,
            @RequestParam Long ownerId,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.listForOwner(ownerType, ownerId, actor);
    }

    /**
     * 갱신 이력: 같은 (owner_type, owner_id, document_type_id) 의 모든 버전.
     * 가장 최신부터 옛 버전 순. previous_document_id 체인을 따라가는 history view 에 사용.
     */
    @GetMapping("/{id}/history")
    public List<DocumentResponse> history(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.history(id, actor);
    }

    /**
     * ADMIN 검토 큐. verification_status = OCR_REVIEW_REQUIRED 또는 REJECTED 인 chain head.
     */
    @GetMapping("/review-queue")
    public List<com.skep.document.dto.ReviewItemResponse> reviewQueue(@CurrentUser AuthenticatedUser actor) {
        return service.reviewQueue(actor);
    }

    /**
     * ADMIN 처리 완료 큐. verifiedAt 이 있는 chain head (자동 + 수동 검증/반려).
     */
    @GetMapping("/processed-queue")
    public List<com.skep.document.dto.ReviewItemResponse> processedQueue(@CurrentUser AuthenticatedUser actor) {
        return service.processedQueue(actor);
    }

    /**
     * ADMIN 만료 임박 큐. days(기본 30) 이내 만료 예정.
     */
    /** 공급사 자기 회사 자원 documents (만료 관리). */
    @GetMapping("/my-supplier")
    public List<com.skep.document.dto.ReviewItemResponse> mySupplierDocs(@CurrentUser AuthenticatedUser actor) {
        return service.mySupplierDocuments(actor);
    }

    @GetMapping("/expiring")
    public List<com.skep.document.dto.ReviewItemResponse> expiringQueue(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(required = false, defaultValue = "30") int days
    ) {
        return service.expiringQueue(actor, days);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @RequestParam OwnerType ownerType,
            @RequestParam Long ownerId,
            @RequestParam Long documentTypeId,
            @RequestParam(required = false) String expiryDate,
            @RequestParam("file") MultipartFile file,
            // S-9-G.2: 사용자 보충 / OCR 미리보기에서 검토한 필드. extracted_data 에 manual_* 키로 저장.
            // 예: manualBizNo, manualOwnerName, manualBusinessName, manualStartDate, manualAddress
            @RequestParam(required = false) java.util.Map<String, String> allParams,
            @CurrentUser AuthenticatedUser actor
    ) {
        LocalDate expiry = parseDate(expiryDate);
        java.util.Map<String, String> manualFields = new java.util.HashMap<>();
        String corners = allParams != null ? allParams.get("corners") : null; // 4모서리 정렬 크롭 저장용
        if (allParams != null) {
            for (var e : allParams.entrySet()) {
                if (e.getKey() != null && e.getKey().startsWith("manual") && e.getValue() != null && !e.getValue().isBlank()) {
                    manualFields.put(e.getKey(), e.getValue());
                }
            }
        }
        return service.upload(ownerType, ownerId, documentTypeId, expiry, file, manualFields, corners, actor);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Document d = service.getForDownload(id, actor);
        Resource res = service.loadFile(d);
        String encodedName = URLEncoder.encode(d.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        // XSS 방어: inline 은 PDF/이미지만. HTML/SVG/오피스 등은 attachment 로 강제 다운로드.
        String disposition = (DocumentService.isInlinePreviewType(d.getContentType()) ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encodedName;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .body(res);
    }

    @PatchMapping("/{id}/expiry")
    public DocumentResponse updateExpiry(
            @PathVariable Long id,
            @RequestParam(required = false) String expiryDate,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.updateExpiry(id, parseDate(expiryDate), actor);
    }

    @PatchMapping("/{id}/verified")
    public DocumentResponse setVerified(
            @PathVariable Long id,
            @RequestParam boolean verified,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.setVerified(id, verified, actor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
