package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType ownerType, Long ownerId);

    long countByVerified(boolean verified);

    @Query("""
            SELECT COUNT(d) FROM Document d
            WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
            """)
    long countExpiringByDate(@Param("maxDate") LocalDate maxDate);

    /** 만료 임박 (모든 owner). ADMIN 전체용. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
            ORDER BY d.expiryDate ASC
            """)
    List<Document> findExpiringAll(@Param("maxDate") LocalDate maxDate);

    /** 만료 임박 (특정 supplier 회사 소유). EQUIPMENT/PERSON 둘 다. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
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
     * 특정 owner들의 만료 임박 서류 수 (group by owner_id).
     * 결과 List<[ownerId, count]> — 0건은 누락.
     */
    @Query("""
            SELECT d.ownerId, COUNT(d) FROM Document d
            WHERE d.ownerType = :ownerType AND d.ownerId IN :ownerIds
              AND d.expiryDate IS NOT NULL AND d.expiryDate <= :maxDate
            GROUP BY d.ownerId
            """)
    List<Object[]> countExpiringGroupedByOwner(
            @Param("ownerType") OwnerType ownerType,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("maxDate") LocalDate maxDate
    );
}
