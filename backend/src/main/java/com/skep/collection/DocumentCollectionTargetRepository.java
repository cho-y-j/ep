package com.skep.collection;

import com.skep.document.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentCollectionTargetRepository extends JpaRepository<DocumentCollectionTarget, Long> {
    List<DocumentCollectionTarget> findByRequestIdOrderBySortOrderAscIdAsc(Long requestId);
    List<DocumentCollectionTarget> findByRequestIdIn(Collection<Long> requestIds);
    List<DocumentCollectionTarget> findByOwnerTypeAndOwnerId(OwnerType ownerType, Long ownerId);
}
