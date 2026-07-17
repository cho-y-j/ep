package com.skep.person;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 인력 역할 × 서류 requirement(junction) 조회 헬퍼. (EquipmentDocRequirementService 의 인력판 미러)
 * PERSON 컴플라이언스/작업계획서 게이트에서 "이 서류가 이 인원 역할에 적용/필수인가"를 판정한다.
 * 행 존재 = 적용, required=true = 필수, 행 없음 = 해당없음.
 *
 * 장비는 종류(category)가 단일이지만 인원은 역할이 다중(Set)이라, 역할 집합에 대해 합집합(적용)·OR(필수)로 병합한다.
 */
@Service
@Transactional(readOnly = true)
public class PersonDocRequirementService {

    private final PersonRoleDocRequirementRepository repo;

    public PersonDocRequirementService(PersonRoleDocRequirementRepository repo) {
        this.repo = repo;
    }

    /** 이 역할들에 적용되는 서류 type id → required 맵. 여러 역할이면 합집합, required 는 OR. (키 존재 = 적용) */
    public Map<Long, Boolean> requiredByDocTypeId(Set<PersonRole> roles) {
        Map<Long, Boolean> m = new HashMap<>();
        for (PersonRole role : roles) {
            for (PersonRoleDocRequirement r : repo.findByPersonRole(role.name())) {
                m.merge(r.getDocumentTypeId(), r.isRequired(), (a, b) -> a || b);
            }
        }
        return m;
    }

    /** 이 역할들에 적용되는 서류 type id 집합(합집합). */
    public Set<Long> applicableDocTypeIds(Set<PersonRole> roles) {
        Set<Long> ids = new HashSet<>();
        for (PersonRole role : roles) {
            for (PersonRoleDocRequirement r : repo.findByPersonRole(role.name())) {
                ids.add(r.getDocumentTypeId());
            }
        }
        return ids;
    }
}
