package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 현장별 안전설정(§3.4) — 폭염 4단계 임계·휴식·무더위 시간대·풍속 중지·일일점검 게이트·정비 주기.
 * 행이 없는 현장은 SafetyThresholds.legalDefault()(= HeatStage 하드코딩) 로 동작(무회귀).
 * 법정 완화 금지 가드는 SiteSafetySettingsService 저장 전 검증.
 */
@Entity
@Table(name = "site_safety_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteSafetySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", nullable = false, unique = true)
    private Long siteId;

    @Column(name = "temp_caution", nullable = false)
    private double tempCaution = 31;

    @Column(name = "temp_warning", nullable = false)
    private double tempWarning = 33;

    @Column(name = "temp_danger", nullable = false)
    private double tempDanger = 35;

    @Column(name = "temp_extreme", nullable = false)
    private double tempExtreme = 38;

    @Column(name = "rest_interval_min", nullable = false)
    private int restIntervalMin = 120;

    @Column(name = "rest_duration_min", nullable = false)
    private int restDurationMin = 20;

    @Column(name = "midday_start_hour", nullable = false)
    private int middayStartHour = 14;

    @Column(name = "midday_end_hour", nullable = false)
    private int middayEndHour = 17;

    @Column(name = "wind_stop_mps", nullable = false)
    private double windStopMps = 10;

    @Column(name = "enforce_daily_inspection_gate", nullable = false)
    private boolean enforceDailyInspectionGate = false;

    /** NULL = 정비 알림 비활성. */
    @Column(name = "maintenance_interval_hours")
    private Integer maintenanceIntervalHours;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private SiteSafetySettings(Long siteId) {
        this.siteId = siteId;
    }

    /** 현장 기본 설정(법정 기본값) 신규 행. */
    public static SiteSafetySettings defaults(Long siteId) {
        return new SiteSafetySettings(siteId);
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

    /** 값 갱신 — 가드 검증은 서비스에서 통과한 뒤 호출. */
    public void apply(double tempCaution, double tempWarning, double tempDanger, double tempExtreme,
                      int restIntervalMin, int restDurationMin, int middayStartHour, int middayEndHour,
                      double windStopMps, boolean enforceDailyInspectionGate, Integer maintenanceIntervalHours,
                      Long updatedBy) {
        this.tempCaution = tempCaution;
        this.tempWarning = tempWarning;
        this.tempDanger = tempDanger;
        this.tempExtreme = tempExtreme;
        this.restIntervalMin = restIntervalMin;
        this.restDurationMin = restDurationMin;
        this.middayStartHour = middayStartHour;
        this.middayEndHour = middayEndHour;
        this.windStopMps = windStopMps;
        this.enforceDailyInspectionGate = enforceDailyInspectionGate;
        this.maintenanceIntervalHours = maintenanceIntervalHours;
        this.updatedBy = updatedBy;
    }
}
