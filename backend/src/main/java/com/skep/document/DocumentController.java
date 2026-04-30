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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @RequestParam OwnerType ownerType,
            @RequestParam Long ownerId,
            @RequestParam Long documentTypeId,
            @RequestParam(required = false) String expiryDate,
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser actor
    ) {
        LocalDate expiry = parseDate(expiryDate);
        return service.upload(ownerType, ownerId, documentTypeId, expiry, file, actor);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Document d = service.getForDownload(id, actor);
        Resource res = service.loadFile(d);
        String encodedName = URLEncoder.encode(d.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedName)
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
