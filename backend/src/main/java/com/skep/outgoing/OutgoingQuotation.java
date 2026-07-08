package com.skep.outgoing;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "outgoing_quotations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutgoingQuotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(name = "sent_by_user_id", nullable = false)
    private Long sentByUserId;

    @Column(name = "equipment_id")
    private Long equipmentId;
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "daily_rate")
    private Integer dailyRate;
    @Column(name = "monthly_rate")
    private Integer monthlyRate;
    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "period_start")
    private LocalDate periodStart;
    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 16)
    private RecipientType recipientType;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;
    @Column(name = "recipient_company_id")
    private Long recipientCompanyId;
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Column(name = "pdf_size")
    private Integer pdfSize;
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
    @Column(name = "mail_sent", nullable = false)
    private boolean mailSent;
    @Column(name = "mail_error", columnDefinition = "text")
    private String mailError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // V37: BP 수락 사인
    @Column(name = "bp_signature_png", columnDefinition = "bytea")
    private byte[] bpSignaturePng;
    @Column(name = "bp_signed_by_user_id")
    private Long bpSignedByUserId;
    @Column(name = "bp_signer_name", length = 100)
    private String bpSignerName;
    @Column(name = "bp_signed_at")
    private LocalDateTime bpSignedAt;

    @Builder
    private OutgoingQuotation(Long supplierCompanyId, Long sentByUserId,
                               Long equipmentId, Long personId,
                               Integer dailyRate, Integer monthlyRate, String note,
                               LocalDate periodStart, LocalDate periodEnd,
                               RecipientType recipientType, Long recipientUserId,
                               Long recipientCompanyId, String recipientEmail) {
        this.supplierCompanyId = supplierCompanyId;
        this.sentByUserId = sentByUserId;
        this.equipmentId = equipmentId;
        this.personId = personId;
        this.dailyRate = dailyRate;
        this.monthlyRate = monthlyRate;
        this.note = note;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.recipientType = recipientType;
        this.recipientUserId = recipientUserId;
        this.recipientCompanyId = recipientCompanyId;
        this.recipientEmail = recipientEmail;
        this.sentAt = LocalDateTime.now();
        this.mailSent = false;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void markMailResult(boolean success, String errorIfAny, Integer pdfSize) {
        this.mailSent = success;
        this.mailError = errorIfAny;
        if (pdfSize != null) this.pdfSize = pdfSize;
    }

    public void applyBpSignature(byte[] png, Long userId, String signerName) {
        this.bpSignaturePng = png;
        this.bpSignedByUserId = userId;
        this.bpSignerName = signerName;
        this.bpSignedAt = LocalDateTime.now();
    }

    public boolean isBpSigned() { return bpSignaturePng != null && bpSignaturePng.length > 0; }

    public enum RecipientType { REGISTERED_BP, EMAIL }
}
