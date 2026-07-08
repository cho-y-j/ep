package com.skep.signature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorksheetSignatureRepository extends JpaRepository<WorksheetSignature, Long> {

    List<WorksheetSignature> findByWorkPlanIdOrderById(Long workPlanId);

    Optional<WorksheetSignature> findByWorkPlanIdAndRole(Long workPlanId, SignatureRole role);

    Optional<WorksheetSignature> findBySignToken(String token);

    long countByWorkPlanIdAndStatus(Long workPlanId, SignatureStatus status);

    /** native query 로 PNG 따로 fetch — JPA byte[] 매핑이 일부 케이스에서 null 로 떨어지는 회피. */
    @Query(value = "SELECT signature_png FROM worksheet_signatures WHERE id = :id", nativeQuery = true)
    byte[] findSignaturePngById(@Param("id") Long id);
}
