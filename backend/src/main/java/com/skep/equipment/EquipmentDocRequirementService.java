package com.skep.equipment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 장비 종류 × 서류 requirement(junction) 조회 헬퍼.
 * EQUIPMENT 컴플라이언스/작업계획서 게이트에서 "이 서류가 이 장비종류에 적용/필수인가"를 판정한다.
 * 행 존재 = 적용, required=true = 필수, 행 없음 = 해당없음.
 */
@Service
@Transactional(readOnly = true)
public class EquipmentDocRequirementService {

    private final EquipmentTypeDocRequirementRepository repo;

    public EquipmentDocRequirementService(EquipmentTypeDocRequirementRepository repo) {
        this.repo = repo;
    }

    /** 이 종류에 적용되는 서류 type id → required 맵. (키 존재 = 적용) */
    public Map<Long, Boolean> requiredByDocTypeId(String equipmentTypeCode) {
        Map<Long, Boolean> m = new HashMap<>();
        for (EquipmentTypeDocRequirement r : repo.findByEquipmentTypeCode(equipmentTypeCode)) {
            m.put(r.getDocumentTypeId(), r.isRequired());
        }
        return m;
    }

    /** 이 종류에 적용되는 서류 type id 집합. */
    public Set<Long> applicableDocTypeIds(String equipmentTypeCode) {
        Set<Long> ids = new HashSet<>();
        for (EquipmentTypeDocRequirement r : repo.findByEquipmentTypeCode(equipmentTypeCode)) {
            ids.add(r.getDocumentTypeId());
        }
        return ids;
    }
}
