package com.skep.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyEquipmentTypeRepository
        extends JpaRepository<CompanyEquipmentType, CompanyEquipmentTypeId> {

    List<CompanyEquipmentType> findByCompanyId(Long companyId);
}
