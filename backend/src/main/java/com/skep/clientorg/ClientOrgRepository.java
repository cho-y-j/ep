package com.skep.clientorg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientOrgRepository extends JpaRepository<ClientOrg, Long> {
    List<ClientOrg> findByActiveOrderByNameAsc(boolean active);
    Optional<ClientOrg> findByCode(String code);
    boolean existsByCode(String code);
}
