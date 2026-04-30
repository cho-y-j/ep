package com.skep.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType ownerType, Long ownerId);
    List<Document> findByExpiryDateLessThanEqualAndExpiryDateGreaterThanEqualOrderByExpiryDateAsc(
            LocalDate maxDate, LocalDate minDate);
}
