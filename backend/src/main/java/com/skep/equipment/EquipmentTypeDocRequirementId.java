package com.skep.equipment;

import java.io.Serializable;
import java.util.Objects;

/** {@link EquipmentTypeDocRequirement} 복합키 (equipment_type_code, document_type_id). */
public class EquipmentTypeDocRequirementId implements Serializable {

    private String equipmentTypeCode;
    private Long documentTypeId;

    public EquipmentTypeDocRequirementId() {}

    public EquipmentTypeDocRequirementId(String equipmentTypeCode, Long documentTypeId) {
        this.equipmentTypeCode = equipmentTypeCode;
        this.documentTypeId = documentTypeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentTypeDocRequirementId that)) return false;
        return Objects.equals(equipmentTypeCode, that.equipmentTypeCode)
                && Objects.equals(documentTypeId, that.documentTypeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(equipmentTypeCode, documentTypeId);
    }
}
