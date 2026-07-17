package com.skep.person;

import java.io.Serializable;
import java.util.Objects;

/** {@link PersonRoleDocRequirement} 복합키 (person_role, document_type_id). */
public class PersonRoleDocRequirementId implements Serializable {

    private String personRole;
    private Long documentTypeId;

    public PersonRoleDocRequirementId() {}

    public PersonRoleDocRequirementId(String personRole, Long documentTypeId) {
        this.personRole = personRole;
        this.documentTypeId = documentTypeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonRoleDocRequirementId that)) return false;
        return Objects.equals(personRole, that.personRole)
                && Objects.equals(documentTypeId, that.documentTypeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personRole, documentTypeId);
    }
}
