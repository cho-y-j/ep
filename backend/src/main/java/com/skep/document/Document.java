package com.skep.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "file_key", nullable = false, length = 255)
    private String fileKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    // V14 검증 컬럼
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 32)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "rejected_reason", length = 255)
    private String rejectedReason;

    @Column(name = "previous_document_id")
    private Long previousDocumentId;

    @Column(name = "verification_result", columnDefinition = "text")
    private String verificationResult;

    @Column(name = "extracted_data", columnDefinition = "text")
    private String extractedData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Document(Long documentTypeId, OwnerType ownerType, Long ownerId,
                     String fileKey, String fileName, long fileSize, String contentType,
                     LocalDate expiryDate, Long uploadedBy,
                     Long previousDocumentId, String extractedData) {
        this.documentTypeId = documentTypeId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.fileKey = fileKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.expiryDate = expiryDate;
        this.uploadedBy = uploadedBy;
        this.previousDocumentId = previousDocumentId;
        this.extractedData = extractedData;
        this.verified = false;
        this.verificationStatus = VerificationStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateExpiry(LocalDate newDate) { this.expiryDate = newDate; }

    public void markVerified() {
        this.verified = true;
        this.verificationStatus = VerificationStatus.VERIFIED;
    }

    public void unmarkVerified() {
        this.verified = false;
        this.verificationStatus = VerificationStatus.PENDING;
        this.verifiedBy = null;
        this.verifiedAt = null;
    }

    public void markVerifiedBy(Long userId) {
        this.verified = true;
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedBy = userId;
        this.verifiedAt = LocalDateTime.now();
        // 이전 OCR_REVIEW/REJECTED 사유 흔적 제거. 검증 완료된 doc 에 옛 사유가 남아있으면 UI 가 모순되게 표시됨.
        this.rejectedReason = null;
    }

    public void markRejected(Long userId, String reason) {
        this.verified = false;
        this.verificationStatus = VerificationStatus.REJECTED;
        this.verifiedBy = userId;
        this.verifiedAt = LocalDateTime.now();
        this.rejectedReason = reason;
    }

    public void markOcrReviewRequired() {
        // S-4 단계 4.1: verification_status 가 VERIFIED 외로 바뀌면 verified boolean 도 반드시 false.
        // OCR_REVIEW_REQUIRED 상태인 doc 이 "유효 서류"로 잘못 계산되어 배차 통과되는 것을 방지.
        this.verified = false;
        this.verificationStatus = VerificationStatus.OCR_REVIEW_REQUIRED;
    }

    public void setRejectedReason(String reason) { this.rejectedReason = reason; }
    public void setVerificationResult(String json) { this.verificationResult = json; }
    public void setExtractedData(String json) { this.extractedData = json; }
}
