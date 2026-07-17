package com.skep.person;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인력 역할 × 서류 요구 junction. (EquipmentTypeDocRequirement 의 인력판 미러)
 * 행 존재 + required=true → 필수, 행 존재 + required=false → 선택, 행 없음 → 해당없음.
 */
@Entity
@Table(name = "person_role_doc_requirement")
@IdClass(PersonRoleDocRequirementId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonRoleDocRequirement {

    @Id
    @Column(name = "person_role", length = 32)
    private String personRole;

    @Id
    @Column(name = "document_type_id")
    private Long documentTypeId;

    @Column(nullable = false)
    private boolean required;

    public PersonRoleDocRequirement(String personRole, Long documentTypeId, boolean required) {
        this.personRole = personRole;
        this.documentTypeId = documentTypeId;
        this.required = required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
