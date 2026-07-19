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

    /** P5-W2 골든타임 t1 — 근접 동료 통보 시각(대응체인 발동 마커). */
    @Column(name = "peer_notified_at")
    private LocalDateTime peerNotifiedAt;

    /** P5-W2 골든타임 t2 — 최초 [제가 갑니다] 응답 시각(최초 1회만). */
    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    /** P5-W2 60초 무응답 → 현장 전체 확대 + 관리자 통보(1회 마커, 무한 확대 방지). */
    @Column(name = "peer_escalated_at")
    private LocalDateTime peerEscalatedAt;

    /** P5-W3 최근 BLE 대리중계 수신 시각(제3자 폰 — 터널·지하 통신불능 보완). */
    @Column(name = "relayed_at")
    private LocalDateTime relayedAt;

    /** P5-W3 중계자 위치 — 피재자 추정 위치 보강(BLE 근거리라 중계자≈피재자). */
    @Column(name = "relay_lat")
    private Double relayLat;

    @Column(name = "relay_lng")
    private Double relayLng;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
