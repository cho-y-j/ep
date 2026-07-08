package com.skep.collection;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 서류 수집 요청에 포함된 서류 1종 (필수/선택 + 업로드된 문서 연결). */
@Entity
@Table(name = "document_collection_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

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
    private DocumentCollectionItem(Long requestId, Long documentTypeId, boolean required, int sortOrder) {
        this.requestId = requestId;
        this.documentTypeId = documentTypeId;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void attachDocument(Long documentId) { this.uploadedDocumentId = documentId; }
}
