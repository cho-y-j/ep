package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P5-W0 워커별 워치 최신 상태(1인 1행) — 데드맨 감시·관제 워커 타일의 원천.
 * "무수신 자체가 신호": last_seen_at 갱신 지연이 곧 데드맨 판정 기준.
 * upsert 지점 = POST /api/field-auth/sensor(폰 배치 중계 + 워치 직접 폴백).
 */
@Entity
@Table(name = "worker_watch_states")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class WorkerWatchState {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** 배터리 잔량 %(0~100). NULL=미보고 — 관제 부족 목록·선제 조치. */
    private Integer battery;

    /** 오프바디(착용) 감지. FALSE=벗음(오경보 방지·착용률 증거), NULL=미보고. */
    private Boolean worn;

    /** GREEN | YELLOW | RED — 서버가 센서 상태 문자열에서 파생(관제 상태등). */
    @Column(length = 16)
    private String state;

    private Integer hr;
    private Integer spo2;

    @Column(name = "body_temp", precision = 4, scale = 1)
    private BigDecimal bodyTemp;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "bp_company_id")
    private Long bpCompanyId;

    /** 열린 데드맨 경보 id — 수신 재개 시 자동 resolve, 재발 방지 가드. */
    @Column(name = "deadman_alert_id")
    private Long deadmanAlertId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
