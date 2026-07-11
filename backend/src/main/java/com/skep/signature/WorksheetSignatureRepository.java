package com.skep.signature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorksheetSignatureRepository extends JpaRepository<WorksheetSignature, Long> {

    List<WorksheetSignature> findByWorkPlanIdOrderById(Long workPlanId);

    Optional<WorksheetSignature> findByWorkPlanIdAndRole(Long workPlanId, SignatureRole role);

    Optional<WorksheetSignature> findBySignToken(String token);

    long countByWorkPlanIdAndStatus(Long workPlanId, SignatureStatus status);

    /** BP 서명대기 위젯 배치 집계 — 여러 작업계획서의 특정 상태 사인 수를 한 번에 (PNG 미로드). Object[]{workPlanId, count}. */
    @Query("SELECT s.workPlanId, COUNT(s) FROM WorksheetSignature s "
            + "WHERE s.workPlanId IN :workPlanIds AND s.status = :status GROUP BY s.workPlanId")
    List<Object[]> countByStatusGroupedByWorkPlan(@Param("workPlanIds") Collection<Long> workPlanIds,
                                                  @Param("status") SignatureStatus status);

    /** native query 로 PNG 따로 fetch — JPA byte[] 매핑이 일부 케이스에서 null 로 떨어지는 회피. */
    @Query(value = "SELECT signature_png FROM worksheet_signatures WHERE id = :id", nativeQuery = true)
    byte[] findSignaturePngById(@Param("id") Long id);
}
