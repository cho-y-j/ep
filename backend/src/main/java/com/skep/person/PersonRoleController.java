package com.skep.person;

import com.skep.common.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 인력 역할별 서류 요구 공개 조회 — 인증 사용자면 접근(FE 등록/서류 화면 체크리스트가 소비).
 * 어드민 3상태 편집은 {@link PersonRoleDocsAdminController}. ({@code EquipmentTypeController} 인력 역할판 미러)
 */
@RestController
@RequestMapping("/api/person-roles")
public class PersonRoleController {

    private final PersonDocRequirementService reqService;

    public PersonRoleController(PersonDocRequirementService reqService) {
        this.reqService = reqService;
    }

    /** 이 역할에 적용되는 서류(junction 행 존재)와 필수 여부. FE 등록화면 체크리스트가 소비. */
    public record DocRequirement(Long documentTypeId, boolean required) {}

    @GetMapping("/{role}/documents")
    public List<DocRequirement> documents(@PathVariable String role) {
        PersonRole pr;
        try {
            pr = PersonRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("PERSON_ROLE_NOT_FOUND", "인력 역할 " + role + " 없음");
        }
        return reqService.requiredByDocTypeId(Set.of(pr)).entrySet().stream()
                .map(e -> new DocRequirement(e.getKey(), e.getValue()))
                .toList();
    }
}
