package com.skep.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByBusinessNumber(String businessNumber);
    boolean existsByBusinessNumber(String businessNumber);
    List<Company> findByType(CompanyType type);
    List<Company> findByParentCompanyId(Long parentCompanyId);
}
