package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ADMIN 이 장비 종류(차종) 마스터를 운영 — 추가/라벨·그룹·순서 수정/숨김(soft delete).
 * code 는 불변 식별자(equipment.category 참조) — 라벨/그룹/순서/활성만 수정.
 * open-in-view=false + @Transactional 부재 → update/deactivate 후 반드시 repo.save().
 */
@RestController
@RequestMapping("/api/admin/equipment-types")
@PreAuthorize("hasRole('ADMIN')")
public class EquipmentTypeAdminController {

    private final EquipmentTypeRepository repo;
    private final DocumentTypeRepository docTypeRepo;
    private final EquipmentTypeDocRequirementRepository reqRepo;

    public EquipmentTypeAdminController(EquipmentTypeRepository repo,
                                        DocumentTypeRepository docTypeRepo,
                                        EquipmentTypeDocRequirementRepository reqRepo) {
        this.repo = repo;
        this.docTypeRepo = docTypeRepo;
        this.reqRepo = reqRepo;
    }

    public record CreateBody(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 16) String grp,
            Integer sortOrder
    ) {}

    public record UpdateBody(
            String name,
            String grp,
            Integer sortOrder,
            Boolean active
    ) {}

    /** 전체(비활성 포함), 정렬 순. */
    @GetMapping
    public List<EquipmentType> list() {
        return repo.findAllByOrderBySortOrderAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentType create(@Valid @RequestBody CreateBody body) {
        String code = body.code().trim();
        if (repo.existsById(code)) {
            throw ApiException.conflict("EQUIPMENT_TYPE_EXISTS", "이미 존재하는 종류 코드: " + code);
        }
        EquipmentType t = new EquipmentType(code, body.name(), body.grp(),
                body.sortOrder() != null ? body.sortOrder() : 0);
        return repo.save(t);
    }

    @PatchMapping("/{code}")
    public EquipmentType update(@PathVariable String code, @RequestBody UpdateBody body) {
        EquipmentType t = repo.findById(code)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_TYPE_NOT_FOUND", "장비 종류 " + code + " 없음"));
        t.update(body.name(), body.grp(), body.sortOrder(), body.active());
        return repo.save(t);   // detached (open-in-view=false) → 명시 save 필요
    }

    @PostMapping("/{code}/deactivate")
    public EquipmentType deactivate(@PathVariable String code) {
        EquipmentType t = repo.findById(code)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_TYPE_NOT_FOUND", "장비 종류 " + code + " 없음"));
        t.update(null, null, null, false);
        return repo.save(t);
    }

    @PostMapping("/{code}/activate")
    public EquipmentType activate(@PathVariable String code) {
        EquipmentType t = repo.findById(code)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_TYPE_NOT_FOUND", "장비 종류 " + code + " 없음"));
        t.update(null, null, null, true);
        return repo.save(t);
    }

    // ── 종류별 서류 체크리스트 (junction) ──

    /** 한 항목 응답 — 전체 활성 EQUIPMENT 서류 + 이 종류에 대한 3상태 + 만료관리 여부. */
    public record DocChecklistItem(Long documentTypeId, String name, boolean hasExpiry, String requirement) {}

    /** 이 종류에 대해 전체 활성 EQUIPMENT 서류 목록 + requirement 상태(REQUIRED/OPTIONAL/NONE). */
    @GetMapping("/{code}/documents")
    public List<DocChecklistItem> documents(@PathVariable String code) {
        if (!repo.existsById(code)) {
            throw ApiException.notFound("EQUIPMENT_TYPE_NOT_FOUND", "장비 종류 " + code + " 없음");
        }
        Map<Long, Boolean> reqByType = new java.util.HashMap<>();
        for (EquipmentTypeDocRequirement r : reqRepo.findByEquipmentTypeCode(code)) {
            reqByType.put(r.getDocumentTypeId(), r.isRequired());
        }
        List<DocumentType> types = docTypeRepo
                .findByAppliesToAndActiveOrderBySortOrderAscIdAsc(OwnerType.EQUIPMENT, true);
        return types.stream().map(t -> {
            String requirement = !reqByType.containsKey(t.getId()) ? "NONE"
                    : (reqByType.get(t.getId()) ? "REQUIRED" : "OPTIONAL");
            return new DocChecklistItem(t.getId(), t.getName(), t.isHasExpiry(), requirement);
        }).toList();
    }

    public record RequirementBody(String requirement) {}   // REQUIRED | OPTIONAL | NONE

    /** 종류-서류 requirement 3상태 저장. NONE=행 삭제, REQUIRED/OPTIONAL=upsert. */
    @PatchMapping("/{code}/documents/{documentTypeId}")
    public void setRequirement(@PathVariable String code, @PathVariable Long documentTypeId,
                               @RequestBody RequirementBody body) {
        if (!repo.existsById(code)) {
            throw ApiException.notFound("EQUIPMENT_TYPE_NOT_FOUND", "장비 종류 " + code + " 없음");
        }
        EquipmentTypeDocRequirementId id = new EquipmentTypeDocRequirementId(code, documentTypeId);
        String req = body.requirement();
        if ("NONE".equals(req)) {
            if (reqRepo.existsById(id)) reqRepo.deleteById(id);
        } else if ("REQUIRED".equals(req) || "OPTIONAL".equals(req)) {
            boolean required = "REQUIRED".equals(req);
            EquipmentTypeDocRequirement row = reqRepo.findById(id).orElse(null);
            if (row != null) {
                row.setRequired(required);
                reqRepo.save(row);
            } else {
                reqRepo.save(new EquipmentTypeDocRequirement(code, documentTypeId, required));
            }
        } else {
            throw ApiException.badRequest("INVALID_REQUIREMENT", "requirement 는 REQUIRED/OPTIONAL/NONE 중 하나여야 합니다");
        }
    }
}
