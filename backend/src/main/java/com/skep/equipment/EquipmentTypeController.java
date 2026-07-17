package com.skep.equipment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 장비 종류 공개 조회 — 인증 사용자면 접근(FE 라벨/드롭다운 동적 소스).
 * 어드민 CRUD 는 {@link EquipmentTypeAdminController}.
 */
@RestController
@RequestMapping("/api/equipment-types")
public class EquipmentTypeController {

    private final EquipmentTypeRepository repo;
    private final EquipmentDocRequirementService reqService;

    public EquipmentTypeController(EquipmentTypeRepository repo, EquipmentDocRequirementService reqService) {
        this.repo = repo;
        this.reqService = reqService;
    }

    /** 활성 종류만, 정렬 순. FE 드롭다운/라벨용 최소 필드. */
    public record Option(String code, String name, String grp) {}

    @GetMapping
    public List<Option> list() {
        return repo.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(t -> new Option(t.getCode(), t.getName(), t.getGrp()))
                .toList();
    }

    /** 이 종류에 적용되는 서류(junction 행 존재)와 필수 여부. FE 등록화면 체크리스트가 소비. */
    public record DocRequirement(Long documentTypeId, boolean required) {}

    @GetMapping("/{code}/documents")
    public List<DocRequirement> documents(@PathVariable String code) {
        return reqService.requiredByDocTypeId(code).entrySet().stream()
                .map(e -> new DocRequirement(e.getKey(), e.getValue()))
                .toList();
    }
}
