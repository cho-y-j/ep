package com.skep.person;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonRoleDocRequirementRepository
        extends JpaRepository<PersonRoleDocRequirement, PersonRoleDocRequirementId> {

    List<PersonRoleDocRequirement> findByPersonRole(String personRole);
}
