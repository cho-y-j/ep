package com.skep.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회사 × 취급 장비종류 junction. 공급사(장비업체)가 자기가 다루는 종류만 선택.
 * 행 없음(빈 집합) = 전체 표시(기존 동작). equipment_type_code 는 equipment_type.code 참조.
 */
@Entity
@Table(name = "company_equipment_type")
@IdClass(CompanyEquipmentTypeId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyEquipmentType {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Id
    @Column(name = "equipment_type_code", length = 32)
    private String equipmentTypeCode;

    public CompanyEquipmentType(Long companyId, String equipmentTypeCode) {
        this.companyId = companyId;
        this.equipmentTypeCode = equipmentTypeCode;
    }
}
