package com.skep.collection;

import com.skep.document.OwnerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 서류 수집 요청 — 공개 토큰 링크로 차량주인/사람이 서류를 무로그인 업로드. */
@Entity
@Table(name = "document_collection_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentCollectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "supplier_company_id")
    private Long supplierCompanyId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(length = 150)
    private String title;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    /** OPEN / SUBMITTED / SENT / CANCELLED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    private DocumentCollectionRequest(String token, LocalDateTime tokenExpiresAt, OwnerType ownerType, Long ownerId,
                                      Long supplierCompanyId, Long createdBy, String title,
                                      String recipientName, String recipientPhone, String recipientEmail) {
        this.token = token;
        this.tokenExpiresAt = tokenExpiresAt;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.supplierCompanyId = supplierCompanyId;
        this.createdBy = createdBy;
        this.title = title;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientEmail = recipientEmail;
        this.status = "OPEN";
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public boolean isExpired() { return tokenExpiresAt != null && tokenExpiresAt.isBefore(LocalDateTime.now()); }

    public void markSubmitted() { this.status = "SUBMITTED"; this.submittedAt = LocalDateTime.now(); }
    public void markSent() { this.status = "SENT"; this.sentAt = LocalDateTime.now(); }
    public void cancel() { this.status = "CANCELLED"; }
}
