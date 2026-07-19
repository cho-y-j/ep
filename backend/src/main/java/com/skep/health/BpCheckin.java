package com.skep.health;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * P5-W4 1겹 — 작업 전/중 혈압 체크인 기록(§4-C). 현장 커프 혈압계 수기 대행 또는 워치/BLE.
 * verdict 는 서버가 현장 임계(BpThresholds)로 계산. 이 행 자체가 "측정하고 조치 권고했다"는 증거사슬.
 */
@Entity
@Table(name = "bp_checkins")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class BpCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(nullable = false)
    private Integer sys;

    @Column(nullable = false)
    private Integer dia;

    private Integer pulse;

    @Column(nullable = false, length = 16)
    private String method = "MANUAL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BpVerdict verdict;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
