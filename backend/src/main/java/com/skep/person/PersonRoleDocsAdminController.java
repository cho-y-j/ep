package com.skep.person;

import com.skep.common.ApiException;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ADMIN 이 인력 역할(PersonRole)별 서류 체크리스트를 운영 — 각 서류를 필수/선택/해당없음 3상태로 지정.
 * ({@code EquipmentTypeAdminController} 의 종류별 서류 체크리스트 부분을 인력 역할판으로 미러)
 * 역할은 PersonRole enum(고정) 이라 역할 자체의 CRUD 는 없다.
 * open-in-view=false + @Transactional 부재 → upsert 후 반드시 repo.save().
 */
@RestController
@RequestMapping("/api/admin/person-roles")
@PreAuthorize("hasRole('ADMIN')")
public class PersonRoleDocsAdminController {

    private final DocumentTypeRepository docTypeRepo;
    private final PersonRoleDocRequirementRepository reqRepo;

    public PersonRoleDocsAdminController(DocumentTypeRepository docTypeRepo,
                                         PersonRoleDocRequirementRepository reqRepo) {
        this.docTypeRepo = docTypeRepo;
        this.reqRepo = reqRepo;
    }

    private static PersonRole parseRole(String role) {
        try {
            return PersonRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("PERSON_ROLE_NOT_FOUND", "인력 역할 " + role + " 없음");
        }
    }

    /** 한 항목 응답 — 전체 활성 PERSON 서류 + 이 역할에 대한 3상태 + 만료관리 여부. */
    public record DocChecklistItem(Long documentTypeId, String name, boolean hasExpiry, String requirement) {}

    /** 이 역할에 대해 전체 활성 PERSON 서류 목록 + requirement 상태(REQUIRED/OPTIONAL/NONE). */
    @GetMapping("/{role}/documents")
    public List<DocChecklistItem> documents(@PathVariable String role) {
        PersonRole pr = parseRole(role);
        Map<Long, Boolean> reqByType = new HashMap<>();
        for (PersonRoleDocRequirement r : reqRepo.findByPersonRole(pr.name())) {
            reqByType.put(r.getDocumentTypeId(), r.isRequired());
        }
        List<DocumentType> types = docTypeRepo
                .findByAppliesToAndActiveOrderBySortOrderAscIdAsc(OwnerType.PERSON, true);
        return types.stream().map(t -> {
            String requirement = !reqByType.containsKey(t.getId()) ? "NONE"
                    : (reqByType.get(t.getId()) ? "REQUIRED" : "OPTIONAL");
            return new DocChecklistItem(t.getId(), t.getName(), t.isHasExpiry(), requirement);
        }).toList();
    }

    public record RequirementBody(String requirement) {}   // REQUIRED | OPTIONAL | NONE

    /** 역할-서류 requirement 3상태 저장. NONE=행 삭제, REQUIRED/OPTIONAL=upsert. */
    @PatchMapping("/{role}/documents/{documentTypeId}")
    public void setRequirement(@PathVariable String role, @PathVariable Long documentTypeId,
                               @RequestBody RequirementBody body) {
        PersonRole pr = parseRole(role);
        PersonRoleDocRequirementId id = new PersonRoleDocRequirementId(pr.name(), documentTypeId);
        String req = body.requirement();
        if ("NONE".equals(req)) {
            if (reqRepo.existsById(id)) reqRepo.deleteById(id);
        } else if ("REQUIRED".equals(req) || "OPTIONAL".equals(req)) {
            boolean required = "REQUIRED".equals(req);
            PersonRoleDocRequirement row = reqRepo.findById(id).orElse(null);
            if (row != null) {
                row.setRequired(required);
                reqRepo.save(row);
            } else {
                reqRepo.save(new PersonRoleDocRequirement(pr.name(), documentTypeId, required));
            }
        } else {
            throw ApiException.badRequest("INVALID_REQUIREMENT", "requirement 는 REQUIRED/OPTIONAL/NONE 중 하나여야 합니다");
        }
    }
}
