package com.skep.person;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findBySupplierIdOrderByIdDesc(Long supplierId);
    List<Person> findAllByOrderByIdDesc();

    @Query("""
            SELECT DISTINCT p FROM Person p JOIN p.roles r
            WHERE r = :role
            ORDER BY p.id DESC
            """)
    List<Person> findByRole(@Param("role") PersonRole role);

    @Query("""
            SELECT DISTINCT p FROM Person p JOIN p.roles r
            WHERE p.supplierId = :supplierId AND r = :role
            ORDER BY p.id DESC
            """)
    List<Person> findBySupplierIdAndRole(@Param("supplierId") Long supplierId, @Param("role") PersonRole role);
}
