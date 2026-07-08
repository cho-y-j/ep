package com.skep.site;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SiteParticipantRepository extends JpaRepository<SiteParticipant, Long> {
    List<SiteParticipant> findBySiteIdOrderByIdDesc(Long siteId);
    List<SiteParticipant> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, SiteParticipantStatus status);
    List<SiteParticipant> findBySiteIdIn(Collection<Long> siteIds);
    Optional<SiteParticipant> findBySiteIdAndCompanyId(Long siteId, Long companyId);
    boolean existsBySiteIdAndCompanyIdAndStatus(Long siteId, Long companyId, SiteParticipantStatus status);
}
