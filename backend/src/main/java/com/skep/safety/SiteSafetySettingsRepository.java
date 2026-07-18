package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SiteSafetySettingsRepository extends JpaRepository<SiteSafetySettings, Long> {

    Optional<SiteSafetySettings> findBySiteId(Long siteId);

    /** 스케줄러 배치 조회(N+1 회피) — 여러 현장 설정 일괄. */
    List<SiteSafetySettings> findBySiteIdIn(Collection<Long> siteIds);

    /** S4'(P3a): 정비 주기가 설정된(활성) 현장 — 가동시간 정비 알림 스케줄러. */
    List<SiteSafetySettings> findByMaintenanceIntervalHoursIsNotNull();
}
