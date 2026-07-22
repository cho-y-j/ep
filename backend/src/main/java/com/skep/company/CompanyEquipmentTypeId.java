package com.skep.company;

import java.io.Serializable;
import java.util.Objects;

/** {@link CompanyEquipmentType} 복합키 (company_id, equipment_type_code). */
public class CompanyEquipmentTypeId implements Serializable {

    private Long companyId;
    private String equipmentTypeCode;

    public CompanyEquipmentTypeId() {}

    public CompanyEquipmentTypeId(Long companyId, String equipmentTypeCode) {
        this.companyId = companyId;
        this.equipmentTypeCode = equipmentTypeCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanyEquipmentTypeId that)) return false;
        return Objects.equals(companyId, that.companyId)
                && Objects.equals(equipmentTypeCode, that.equipmentTypeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, equipmentTypeCode);
    }
}
