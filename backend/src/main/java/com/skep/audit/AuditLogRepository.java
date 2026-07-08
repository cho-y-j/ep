package com.skep.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** ADMIN 전체 조회. */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 회사 관리자가 아닌 일반 직원이 자기 행동 로그만 조회. */
    Page<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId, Pageable pageable);

    /**
     * 특정 회사 관점의 로그.
     * - 그 회사가 actor_company_id 인 경우 (그 회사가 행위 주체)
     * - 그 회사가 target_company_id 인 경우 (그 회사 자원이 대상)
     * - 그 회사가 소유한 사이트의 site_id 인 경우 (BP) — 호출자가 sites_for_company 를 별도로 묶어 site_id IN 조건으로 결합 가능. 여기서는 단순화로 actor/target 만.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.actorCompanyId = :companyId
               OR a.targetCompanyId = :companyId
               OR a.siteId IN :siteIds
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findForCompanyScope(@Param("companyId") Long companyId,
                                       @Param("siteIds") java.util.Collection<Long> siteIds,
                                       Pageable pageable);
}
