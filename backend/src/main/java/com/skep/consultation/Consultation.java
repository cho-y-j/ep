package com.skep.consultation;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 상담 요청 — 공개 랜딩에서 비로그인 방문자가 남기는 도입 문의(V117).
 * 저장 즉시 ADMIN 시스템 알림 1건. ADMIN 이 목록 확인 후 handled 처리.
 */
@Entity
@Table(name = "consultations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(nullable = false, length = 64)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "text")
    private String message;

    @Column(nullable = false)
    private boolean handled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Consultation create(String companyName, String contactName, String phone,
                                      String email, String message) {
        Consultation c = new Consultation();
        c.companyName = companyName;
        c.contactName = contactName;
        c.phone = phone;
        c.email = email;
        c.message = message;
        c.handled = false;
        c.createdAt = LocalDateTime.now();
        return c;
    }

    public void markHandled() {
        this.handled = true;
    }
}
