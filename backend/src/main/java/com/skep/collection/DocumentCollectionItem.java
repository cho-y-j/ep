package com.skep.collection;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 수집 요청의 대상 1건에 포함된 서류 1종 (필수/선택 + 업로드된 문서 연결). */
@Entity
@Table(name = "document_collection_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** V118: target 소속이지만 요청 단위 조회 편의로 유지. */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "uploaded_document_id")
    private Long uploadedDocumentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private DocumentCollectionItem(Long requestId, Long targetId, Long documentTypeId, boolean required, int sortOrder) {
        this.requestId = requestId;
        this.targetId = targetId;
        this.documentTypeId = documentTypeId;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void attachDocument(Long documentId) { this.uploadedDocumentId = documentId; }
}
