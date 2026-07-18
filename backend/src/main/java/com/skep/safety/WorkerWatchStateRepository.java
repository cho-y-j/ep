package com.skep.safety;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerWatchStateRepository extends JpaRepository<WorkerWatchState, Long> {

    /** 관제 워커 타일 — 현장 스코프. */
    List<WorkerWatchState> findBySiteId(Long siteId);
}
