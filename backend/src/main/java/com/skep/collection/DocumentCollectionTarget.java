package com.skep.collection;

import com.skep.document.OwnerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 수집 요청의 대상 1건(장비 또는 인원). 요청 1건(토큰 1개)에 대상 N개, 대상마다 서류 N개. */
@Entity
@Table(name = "document_collection_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentCollectionTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DocumentCollectionTarget(Long requestId, OwnerType ownerType, Long ownerId, int sortOrder) {
        this.requestId = requestId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
