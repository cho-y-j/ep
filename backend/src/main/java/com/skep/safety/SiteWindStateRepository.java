package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteWindStateRepository extends JpaRepository<SiteWindState, Long> {
    Optional<SiteWindState> findBySiteId(Long siteId);
}
