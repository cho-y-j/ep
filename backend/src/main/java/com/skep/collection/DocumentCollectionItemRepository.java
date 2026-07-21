package com.skep.collection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentCollectionItemRepository extends JpaRepository<DocumentCollectionItem, Long> {
    List<DocumentCollectionItem> findByRequestIdOrderBySortOrderAscIdAsc(Long requestId);
    List<DocumentCollectionItem> findByRequestIdInOrderBySortOrderAscIdAsc(Collection<Long> requestIds);
}
