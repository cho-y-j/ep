package com.skep.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteRepository extends JpaRepository<Site, Long> {
    List<Site> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);
    List<Site> findAllByOrderByIdDesc();
}
