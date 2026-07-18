package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * S1 강풍 작업중지 전이 상태(현장별) — 초과 진입 1회·해제 1회 발송(스팸 방지)의 마지막 상태 저장소.
 * 진입/해제 시각은 안전 증거 사슬(고지 시각 기록)로도 사용.
 */
@Entity
@Table(name = "site_wind_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteWindState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false, unique = true)
    private Long siteId;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "wind_mps")
    private Double windMps;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private SiteWindState(Long siteId) {
        this.siteId = siteId;
        this.updatedAt = LocalDateTime.now();
    }

    public static SiteWindState of(Long siteId) {
        return new SiteWindState(siteId);
    }

    /** 임계 초과 진입. */
    public void enter(double windMps, LocalDateTime when) {
        this.active = true;
        this.windMps = windMps;
        this.enteredAt = when;
        this.updatedAt = when;
    }

    /** 임계 이하 복귀(해제). */
    public void clear(Double windMps, LocalDateTime when) {
        this.active = false;
        this.windMps = windMps;
        this.clearedAt = when;
        this.updatedAt = when;
    }
}
