package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    List<DocumentType> findByAppliesToAndActiveOrderBySortOrderAscIdAsc(OwnerType appliesTo, boolean active);
    List<DocumentType> findAllByOrderByAppliesToAscSortOrderAscIdAsc();
}
