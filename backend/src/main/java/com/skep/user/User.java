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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(String email, String password, String name, String phone,
                 Role role, Long companyId, boolean isCompanyAdmin, boolean enabled) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.companyId = companyId;
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
}
