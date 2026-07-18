package com.skep.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteRepository extends JpaRepository<Site, Long> {
    List<Site> findByBpCompanyIdOrderByIdDesc(Long bpCompanyId);
    List<Site> findAllByOrderByIdDesc();

    /** 원청(client_org) 관제 — 그 원청에 연결된 현장만. */
    List<Site> findByClientOrgIdOrderByIdDesc(Long clientOrgId);
}
