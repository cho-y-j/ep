package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType ownerType, Long ownerId);

    long countByVerified(boolean verified);

    /** 공급사 자기+직속 자식(협력사) 회사 자원(장비+인원) + 회사 documents — head 만. 만료 관리 페이지용. */
    @Query("""
            SELECT d FROM Document d, DocumentType dt
            WHERE dt.id = d.documentTypeId
            AND (
                (d.ownerType = com.skep.document.OwnerType.EQUIPMENT
                    AND d.ownerId IN (SELECT e.id FROM com.skep.equipment.Equipment e WHERE e.supplierId IN :companyIds))
                OR (d.ownerType = com.skep.document.OwnerType.PERSON
                    AND d.ownerId IN (SELECT p.id FROM com.skep.person.Person p WHERE p.supplierId IN :companyIds))
                OR (d.ownerType = com.skep.document.OwnerType.COMPANY AND d.ownerId IN :companyIds)
            )
            AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            ORDER BY d.ownerType ASC, d.ownerId ASC, dt.sortOrder ASC, d.id DESC
            """)
    List<Document> findMySupplierDocuments(@Param("companyIds") List<Long> companyIds);

    /**
     * 재등록 체인: 같은 (owner_type, owner_id, document_type_id) 의 가장 최신 문서.
     * upload 시 previous_document_id 로 묶기 위해 사용한다.
     */
    java.util.Optional<Document> findTopByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc(
            OwnerType ownerType, Long ownerId, Long documentTypeId);

    /**
     * 갱신 이력: 같은 (owner_type, owner_id, document_type_id) 의 모든 row id desc.
     * head 부터 옛 버전까지 시간순 보여주는 history view 에 사용.
     */
    List<Document> findByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc(
            OwnerType ownerType, Long ownerId, Long documentTypeId);

    /**
     * 자원의 활성(체인 head) 문서들. previous_document_id 로 자신을 가리키는 후속 문서가 없는 row 만.
     * 같은 type 에 옛 버전이 있어도 표시는 최신 1개만.
     */
    @Query("""
            SELECT d FROM Document d, DocumentType dt
            WHERE dt.id = d.documentTypeId
              AND d.ownerType = :ownerType AND d.ownerId = :ownerId
              AND NOT EXISTS (
                SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id
              )
            ORDER BY dt.sortOrder ASC, d.id DESC
            """)
    List<Document> findActiveHeadByOwner(@Param("ownerType") OwnerType ownerType,
                                         @Param("ownerId") Long ownerId);

    /**
     * 회사 자원 owner_id 들의 위험 서류 — 만료 임박(<=maxDate) 또는 REJECTED / OCR_REVIEW_REQUIRED.
     * 공급사/BP 대시보드의 document_risks 채우기에 사용. chain head 만 (옛 버전 제외).
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.ownerType = :ownerType AND d.ownerId IN :ownerIds
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
              AND (
                (d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate)
                OR d.verificationStatus = com.skep.document.VerificationStatus.REJECTED
                OR d.verificationStatus = com.skep.document.VerificationStatus.OCR_REVIEW_REQUIRED
              )
            ORDER BY d.expiryDate ASC, d.id DESC
            """)
    List<Document> findRiskyForOwners(@Param("ownerType") OwnerType ownerType,
                                      @Param("ownerIds") List<Long> ownerIds,
                                      @Param("maxDate") LocalDate maxDate);

    /**
     * OCR 검토 필요 큐 — verification_status 가 OCR_REVIEW_REQUIRED 또는 REJECTED 인 chain head.
     * ADMIN 검토 페이지에 사용.
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.verificationStatus IN (
                    com.skep.document.VerificationStatus.OCR_REVIEW_REQUIRED,
                    com.skep.document.VerificationStatus.REJECTED)
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            ORDER BY d.id DESC
            """)
    List<Document> findReviewQueue();

    /**
     * ADMIN 이 처리 완료한 서류 — VERIFIED 인 chain head 중 verified_by 가 있는 (수동 처리) 또는
     * 자동 검증 통과 (verified_at 있음). REJECTED 도 처리 완료에 포함 (반려 = 처리됨).
     */
    @Query("""
            SELECT d FROM Document d
            WHERE d.verifiedAt IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            ORDER BY d.verifiedAt DESC
            """)
    List<Document> findProcessedQueue();

    @Query("""
            SELECT COUNT(d) FROM Document d
            WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            """)
    long countExpiringByDate(@Param("maxDate") LocalDate maxDate);

    /** 만료 임박 (모든 owner). ADMIN 전체용. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            ORDER BY d.expiryDate ASC
            """)
    List<Document> findExpiringAll(@Param("maxDate") LocalDate maxDate);

    /** 만료 임박 (특정 supplier 회사 소유). EQUIPMENT/PERSON 둘 다. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
              AND (
                (d.ownerType = com.skep.document.OwnerType.EQUIPMENT AND d.ownerId IN
                    (SELECT e.id FROM com.skep.equipment.Equipment e WHERE e.supplierId = :companyId))
                OR
                (d.ownerType = com.skep.document.OwnerType.PERSON AND d.ownerId IN
                    (SELECT p.id FROM com.skep.person.Person p WHERE p.supplierId = :companyId))
              )
            ORDER BY d.expiryDate ASC
            """)
    List<Document> findExpiringForCompany(@Param("companyId") Long companyId, @Param("maxDate") LocalDate maxDate);

    long countByOwnerTypeAndOwnerIdIn(OwnerType ownerType, List<Long> ownerIds);

    /**
     * V82: OCR 백필 — expiry_date 가 아직 NULL 인 경우에만 만료일을 채운다.
     * 타깃 UPDATE(전체 row rewrite 아님)라 AutoVerify 등 다른 async write 와의 lost-update 회피.
     * expiry_date IS NULL 가드로 사용자가 그 사이 직접 입력한 값을 오탐이 덮지 않음(멱등).
     */
    @Modifying
    @Query("update Document d set d.expiryDate = :date, d.updatedAt = :now "
            + "where d.id = :id and d.expiryDate is null")
    int updateExpiryIfNull(@Param("id") Long id, @Param("date") LocalDate date, @Param("now") LocalDateTime now);

    /** S-11: 회사 사업자등록증 게이트 검사 — chain head VERIFIED 인 서류가 있는지. */
    @Query("""
            SELECT d FROM Document d
            JOIN com.skep.document.DocumentType t ON t.id = d.documentTypeId
            WHERE d.ownerType = :ownerType AND d.ownerId = :ownerId
              AND t.name = :typeName
              AND d.verificationStatus = :status
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            ORDER BY d.id DESC
            """)
    java.util.List<Document> findChainHeadByOwnerAndTypeName(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerId") Long ownerId,
            @Param("typeName") String typeName,
            @Param("status") VerificationStatus status);

    /** 특정 owner_type + owner_ids 의 만료 임박 서류 수. 공급사 대시보드 owner_type 별 카운트에 사용. */
    @Query("""
            SELECT COUNT(d) FROM Document d
            WHERE d.ownerType = :ownerType AND d.ownerId IN :ownerIds
              AND d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            """)
    long countExpiringForOwners(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("maxDate") LocalDate maxDate
    );

    /**
     * 자원 후보용: 자원들이 보유한 "유효한" 서류의 (ownerId, document_type_id) 페어 목록.
     *
     * 유효 = chain head (후속 갱신 row 없음)
     *      + verification_status = VERIFIED
     *      + verified = true (이중 안전망)
     *      + 만료 안 됨 (expiry_date NULL 또는 >= today).
     *
     * S-4 단계 4.1 정책 강화:
     *  - 옛 VERIFIED 문서가 최신 REJECTED/OCR_REVIEW_REQUIRED 갱신본에 가려졌을 때, 옛 row 가
     *    "유효 서류"로 잘못 계산되어 배차가 통과되는 우회를 막기 위해 chain head 만 허용.
     *  - REJECTED / OCR_REVIEW_REQUIRED / PENDING 은 자동 제외.
     *
     * 결과를 Service 측에서 ownerId 별 type_id 셋으로 그룹핑하여 missing_documents 및
     * 배차 차단(`enforceAssignmentDocs`) 판정에 사용한다.
     */
    @Query("""
            SELECT d.ownerId, d.documentTypeId FROM Document d
            WHERE d.ownerType = :ownerType AND d.ownerId IN :ownerIds
              AND d.verificationStatus = com.skep.document.VerificationStatus.VERIFIED
              AND d.verified = true
              AND (d.expiryDate IS NULL OR d.expiryDate >= :today)
              AND NOT EXISTS (
                SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id
              )
            """)
    List<Object[]> findValidVerifiedTypesByOwners(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("today") LocalDate today
    );

    /**
     * 특정 owner들의 만료 임박 서류 수 (group by owner_id).
     * 결과 List<[ownerId, count]> — 0건은 누락.
     */
    @Query("""
            SELECT d.ownerId, COUNT(d) FROM Document d
            WHERE d.ownerType = :ownerType AND d.ownerId IN :ownerIds
              AND d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
              AND NOT EXISTS (SELECT 1 FROM Document d2 WHERE d2.previousDocumentId = d.id)
            GROUP BY d.ownerId
            """)
    List<Object[]> countExpiringGroupedByOwner(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("maxDate") LocalDate maxDate
    );

    /** 특정 owner들의 전체 서류 수 (group by owner_id). 0건은 누락. */
    @Query("""
            SELECT d.ownerId, COUNT(d) FROM Document d
            WHERE d.ownerType = :ownerType AND d.ownerId IN :ownerIds
            GROUP BY d.ownerId
            """)
    List<Object[]> countTotalGroupedByOwner(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds
    );
}
