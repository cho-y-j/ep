package com.skep.person;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, Long> {

    long countBySupplierId(Long supplierId);

    /**
     * 통합 검색/필터/페이지네이션 쿼리.
     * - q: name 또는 phone 부분 일치 (대소문자 무시). 빈 문자열이면 무시.
     * - supplierId: null이면 전체.
     * - role: null이면 전체. 있으면 그 role 가진 사람만 (다대다 조인).
     *
     * NOTE: q를 IS NULL 비교 대신 sentinel 빈 문자열로 처리. PostgreSQL이 nullable 파라미터의
     *       타입을 추론 못 해서 bytea로 잡히는 이슈 회피.
     */
    @Query(value = """
            SELECT DISTINCT p FROM Person p LEFT JOIN p.roles r
            WHERE (:supplierId IS NULL OR p.supplierId = :supplierId)
              AND (:role IS NULL OR r = :role)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR (p.phone IS NOT NULL AND LOWER(p.phone) LIKE LOWER(CONCAT('%', :q, '%'))))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p) FROM Person p LEFT JOIN p.roles r
            WHERE (:supplierId IS NULL OR p.supplierId = :supplierId)
              AND (:role IS NULL OR r = :role)
              AND (:q = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR (p.phone IS NOT NULL AND LOWER(p.phone) LIKE LOWER(CONCAT('%', :q, '%'))))
            """)
    Page<Person> search(
            @Param("supplierId") Long supplierId,
            @Param("role") PersonRole role,
            @Param("q") String q,
            Pageable pageable
    );
}
