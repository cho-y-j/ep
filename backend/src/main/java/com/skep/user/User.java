package com.skep.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 32)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Column(name = "company_id")
    private Long companyId;

    /** CLIENT(원청) 역할 전용 — 소속 원청. 그 외 역할은 NULL. */
    @Column(name = "client_org_id")
    private Long clientOrgId;

    @Column(name = "is_company_admin", nullable = false)
    private boolean isCompanyAdmin;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "show_in_quote", nullable = false)
    private boolean showInQuote;

    @Column(name = "quote_display_order")
    private Integer quoteDisplayOrder;

    /** BP/ADMIN 모바일 앱 FCM 토큰 — 작업자 현장 문제알림 푸시 수신용. 로그인 후 register-fcm-token 으로 등록. */
    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    /** 발송 메일 계정(심사 메일 From) — 본인 메일로 보내려는 사용자만 등록. 미설정 시 시스템 기본 계정 발송. */
    @Column(name = "mail_sender_email", length = 255)
    private String mailSenderEmail;

    /** 발송 메일 계정 앱 비밀번호 — AES-GCM 암호문(평문 저장 금지). */
    @Column(name = "mail_sender_password_enc", length = 512)
    private String mailSenderPasswordEnc;

    /** 보낸사람 표시명 — 없으면 사용자명으로 대체. */
    @Column(name = "mail_sender_name", length = 100)
    private String mailSenderName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(String email, String password, String name, String phone,
                 Role role, Long companyId, Long clientOrgId, boolean isCompanyAdmin, boolean enabled) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.companyId = companyId;
        this.clientOrgId = clientOrgId;
        this.isCompanyAdmin = isCompanyAdmin;
        this.enabled = enabled;
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

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** ADMIN 승인 시 첫 master 자동 부여용. signup 시점에는 호출 X. */
    public void setIsCompanyAdmin(boolean isCompanyAdmin) {
        this.isCompanyAdmin = isCompanyAdmin;
    }

    public void updateProfile(String name, String phone) {
        if (name != null && !name.isBlank()) this.name = name;
        if (phone != null) this.phone = phone;
    }

    public void updateQuoteVisibility(boolean show, Integer order) {
        this.showInQuote = show;
        this.quoteDisplayOrder = order;
    }

    public void updateFcmToken(String token) {
        this.fcmToken = token;
    }

    /** 발송 메일 계정 등록/갱신. passwordEnc 는 이미 암호화된 값. */
    public void updateMailSender(String email, String passwordEnc, String name) {
        this.mailSenderEmail = email;
        this.mailSenderPasswordEnc = passwordEnc;
        this.mailSenderName = name;
    }

    /** 발송 메일 계정 해제 — 시스템 기본 계정 발송으로 폴백. */
    public void clearMailSender() {
        this.mailSenderEmail = null;
        this.mailSenderPasswordEnc = null;
        this.mailSenderName = null;
    }
}
