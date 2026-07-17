package com.skep.equipment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비 종류(equipment_type) 라벨/유효성 조회 헬퍼.
 * enum(EquipmentCategory) 소멸 후 code→표시이름 해석과 등록 시 유효 코드 검증의 단일 소스.
 */
@Service
@Transactional(readOnly = true)
public class EquipmentTypeService {

    private final EquipmentTypeRepository repo;

    public EquipmentTypeService(EquipmentTypeRepository repo) {
        this.repo = repo;
    }

    /** 종류 code → 표시 이름. 미등록(삭제/신규) code 는 code 그대로 반환. null → null. */
    public String labelOf(String code) {
        if (code == null) return null;
        return repo.findById(code).map(EquipmentType::getName).orElse(code);
    }

    /** 활성 종류로 등록된 code 인가? 장비 등록/수정 쓰기 게이트용. */
    public boolean existsActive(String code) {
        return code != null && repo.findById(code).map(EquipmentType::isActive).orElse(false);
    }
}
