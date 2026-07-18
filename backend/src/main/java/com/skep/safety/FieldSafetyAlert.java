package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_safety_alerts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FieldSafetyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "work_plan_id")
    private Long workPlanId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "bp_company_id")
    private Long bpCompanyId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, length = 16)
    private String level;

    /** S5' 등급 — EMERGENCY | CAUTION | NORMAL. NULL=레거시(일반, ack 불요). */
    @Column(length = 16)
    private String severity;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Integer hr;
    private Integer spo2;

    @Column(name = "body_temp", precision = 4, scale = 1)
    private BigDecimal bodyTemp;

    private Integer stress;
    private Double lat;
    private Double lng;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    /** S5' 확인응답 — 작업자 [확인] 탭 시각(인지 증거). resolved(관제 처리완료)와 별개. */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    /** S5' 확인한 작업자(본인). */
    @Column(name = "ack_person_id")
    private Long ackPersonId;

    /** S5' 5분 미확인 → 재알림 1회 + 관제 표시 시각(재알림 무한 스팸 방지 마커). */
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
