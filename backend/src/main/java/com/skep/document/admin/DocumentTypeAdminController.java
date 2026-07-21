package com.skep.document.admin;

import com.skep.common.ApiException;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * S-11: ADMIN 이 document_types catalog 운영 — 새 서류 type 추가, 카테고리/역할 매핑 조정, 정책 변경.
 */
@RestController
@RequestMapping("/api/admin/document-types")
@PreAuthorize("hasRole('ADMIN')")
public class DocumentTypeAdminController {

    private final DocumentTypeRepository repo;

    public DocumentTypeAdminController(DocumentTypeRepository repo) {
        this.repo = repo;
    }

    public record CreateBody(
            @NotBlank @Size(max = 100) String name,
            @NotNull OwnerType appliesTo,
            boolean hasExpiry,
            boolean requiresVerification,
            Integer sortOrder,
            boolean required,
            boolean blocksAssignment,
            Integer defaultValidMonths,
            String appliesToCategories,
            String appliesToPersonRoles
    ) {}

    public record UpdateBody(
            String name,
            Boolean hasExpiry,
            Boolean requiresVerification,
            Integer sortOrder,
            Boolean required,
            Boolean blocksAssignment,
            Integer defaultValidMonths,
            String appliesToCategories,    // null 이면 변경 안 함, 빈 문자열이면 매핑 해제
            String appliesToPersonRoles,
            String ocrRegionTemplate,      // null 이면 변경 안 함, 빈 문자열이면 템플릿 해제
            String sampleDescription,      // V119: null 이면 변경 안 함, 빈 문자열이면 설명 해제
            Boolean active
    ) {}

    @GetMapping
    public List<DocumentType> list() {
        return repo.findAllByOrderByAppliesToAscSortOrderAscIdAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentType create(@Valid @RequestBody CreateBody body) {
        DocumentType t = DocumentType.builder()
                .name(body.name())
                .appliesTo(body.appliesTo())
                .hasExpiry(body.hasExpiry())
                .requiresVerification(body.requiresVerification())
                .sortOrder(body.sortOrder())
                .active(true)
                .required(body.required())
                .blocksAssignment(body.blocksAssignment())
                .defaultValidMonths(body.defaultValidMonths())
                .ocrEnabled(false)
                .build();
        t.setAppliesToCategories(body.appliesToCategories());
        t.setAppliesToPersonRoles(body.appliesToPersonRoles());
        return repo.save(t);
    }

    @PatchMapping("/{id}")
    public DocumentType update(@PathVariable Long id, @RequestBody UpdateBody body) {
        DocumentType t = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("DOCUMENT_TYPE_NOT_FOUND",
                        "서류 타입 " + id + " 없음"));
        if (body.name() != null) t.setName(body.name());
        if (body.hasExpiry() != null) t.setHasExpiry(body.hasExpiry());
        if (body.requiresVerification() != null) t.setRequiresVerification(body.requiresVerification());
        if (body.sortOrder() != null) t.setSortOrder(body.sortOrder());
        if (body.required() != null) t.setRequired(body.required());
        if (body.blocksAssignment() != null) t.setBlocksAssignment(body.blocksAssignment());
        if (body.defaultValidMonths() != null) t.setDefaultValidMonths(body.defaultValidMonths());
        if (body.appliesToCategories() != null)
            t.setAppliesToCategories(body.appliesToCategories().isBlank() ? null : body.appliesToCategories());
        if (body.appliesToPersonRoles() != null)
            t.setAppliesToPersonRoles(body.appliesToPersonRoles().isBlank() ? null : body.appliesToPersonRoles());
        if (body.ocrRegionTemplate() != null)
            t.setOcrRegionTemplate(body.ocrRegionTemplate().isBlank() ? null : body.ocrRegionTemplate());
        if (body.sampleDescription() != null)
            t.setSampleDescription(body.sampleDescription().isBlank() ? null : body.sampleDescription());
        if (body.active() != null) {
            if (body.active()) t.activate(); else t.deactivate();
        }
        return repo.save(t);   // open-in-view=false + @Transactional 부재 → detached 이므로 명시 save 필요(선재 버그 수정)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        DocumentType t = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("DOCUMENT_TYPE_NOT_FOUND",
                        "서류 타입 " + id + " 없음"));
        t.deactivate();
        repo.save(t);   // detached → 명시 save (update()와 동일 선재 버그)
    }
}
