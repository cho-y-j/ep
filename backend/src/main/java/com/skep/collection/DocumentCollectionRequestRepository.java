package com.skep.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentCollectionRequestRepository extends JpaRepository<DocumentCollectionRequest, Long> {
    Optional<DocumentCollectionRequest> findByToken(String token);
    List<DocumentCollectionRequest> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
    List<DocumentCollectionRequest> findByOwnerTypeAndOwnerIdOrderByIdDesc(com.skep.document.OwnerType ownerType, Long ownerId);
    boolean existsByToken(String token);
}
