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

    /** 등록형은 공개 링크에서 값 입력 순간 자원을 만들며 채워진다. 그 전까지 NULL(미등록 슬롯). */
    @Column(name = "owner_id")
    private Long ownerId;

    /** 등록형에서 무엇을 만들지 — EQUIPMENT=장비종류 code, PERSON=역할 name. 갱신형은 NULL. */
    @Column(name = "planned_type", length = 32)
    private String plannedType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DocumentCollectionTarget(Long requestId, OwnerType ownerType, Long ownerId, String plannedType, int sortOrder) {
        this.requestId = requestId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.plannedType = plannedType;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    /** 등록형 — 공개 링크에서 자원을 만든 뒤 그 owner id 를 슬롯에 연결. */
    public void linkOwner(Long ownerId) { this.ownerId = ownerId; }
}
