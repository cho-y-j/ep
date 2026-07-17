package com.skep.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장비 종류 × 서류 요구 junction.
 * 행 존재 + required=true → 필수, 행 존재 + required=false → 선택, 행 없음 → 해당없음.
 */
@Entity
@Table(name = "equipment_type_doc_requirement")
@IdClass(EquipmentTypeDocRequirementId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentTypeDocRequirement {

    @Id
    @Column(name = "equipment_type_code", length = 32)
    private String equipmentTypeCode;

    @Id
    @Column(name = "document_type_id")
    private Long documentTypeId;

    @Column(nullable = false)
    private boolean required;

    public EquipmentTypeDocRequirement(String equipmentTypeCode, Long documentTypeId, boolean required) {
        this.equipmentTypeCode = equipmentTypeCode;
        this.documentTypeId = documentTypeId;
        this.required = required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
