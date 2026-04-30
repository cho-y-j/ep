package com.skep.document;

import com.skep.common.ApiException;
import com.skep.document.dto.CreateDocumentTypeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DocumentTypeService {

    private final DocumentTypeRepository repo;

    public DocumentTypeService(DocumentTypeRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<DocumentType> listForOwner(OwnerType appliesTo) {
        return repo.findByAppliesToAndActiveOrderBySortOrderAscIdAsc(appliesTo, true);
    }

    @Transactional(readOnly = true)
    public List<DocumentType> listAll() {
        return repo.findAllByOrderByAppliesToAscSortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public DocumentType get(Long id) {
        return repo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_TYPE_NOT_FOUND", "document type " + id + " not found"));
    }

    public DocumentType create(CreateDocumentTypeRequest req) {
        return repo.save(DocumentType.builder()
                .name(req.name())
                .appliesTo(req.appliesTo())
                .hasExpiry(req.hasExpiry())
                .requiresVerification(req.requiresVerification())
                .sortOrder(req.sortOrder())
                .active(true)
                .build());
    }

    public DocumentType activate(Long id, boolean active) {
        DocumentType t = get(id);
        if (active) t.activate(); else t.deactivate();
        return t;
    }
}
