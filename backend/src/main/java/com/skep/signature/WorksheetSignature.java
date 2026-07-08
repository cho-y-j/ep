package com.skep.signature;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "worksheet_signatures")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class WorksheetSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_plan_id", nullable = false)
    private Long workPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private SignatureRole role;

    @Column(name = "signer_name", length = 100)
    private String signerName;

    @Column(name = "signer_email", length = 255)
    private String signerEmail;

    @Column(name = "sign_token", length = 64, unique = true)
    private String signToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "signature_png", columnDefinition = "bytea")
    private byte[] signaturePng;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "signed_by_user_id")
    private Long signedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SignatureStatus status = SignatureStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
