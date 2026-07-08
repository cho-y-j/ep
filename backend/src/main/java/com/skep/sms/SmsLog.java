package com.skep.sms;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "sms_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 64)
    private String purpose;

    @Column(nullable = false, length = 16)
    private String status;  // PENDING / SENT / FAILED / DISABLED

    @Column(length = 32)
    private String provider;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private SmsLog(String phone, String message, String purpose, String status,
                   String provider, String externalId, String errorMessage, Long sentBy) {
        this.phone = phone;
        this.message = message;
        this.purpose = purpose;
        this.status = status;
        this.provider = provider;
        this.externalId = externalId;
        this.errorMessage = errorMessage;
        this.sentBy = sentBy;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
