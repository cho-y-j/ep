package com.skep.document;

import com.skep.document.dto.CreateDocumentTypeRequest;
import com.skep.document.dto.DocumentTypeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
}
