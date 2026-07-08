package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_baselines")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FieldBaseline {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "hr_rest_mean", precision = 5, scale = 2)
    private BigDecimal hrRestMean;

    @Column(name = "hr_rest_std", precision = 5, scale = 2)
    private BigDecimal hrRestStd;

    @Column(name = "hr_active_mean", precision = 5, scale = 2)
    private BigDecimal hrActiveMean;

    @Column(name = "spo2_mean", precision = 5, scale = 2)
    private BigDecimal spo2Mean;

    @Column(name = "spo2_std", precision = 5, scale = 2)
    private BigDecimal spo2Std;

    @Column(name = "body_temp_mean", precision = 4, scale = 2)
    private BigDecimal bodyTempMean;

    @Column(name = "body_temp_std", precision = 4, scale = 2)
    private BigDecimal bodyTempStd;

    @Column(name = "accel_baseline_mean", precision = 6, scale = 3)
    private BigDecimal accelBaselineMean;

    @Column(name = "accel_baseline_std", precision = 6, scale = 3)
    private BigDecimal accelBaselineStd;

    @Column(name = "alert_hr_upper", precision = 5, scale = 2)
    private BigDecimal alertHrUpper;

    @Column(name = "alert_hr_lower", precision = 5, scale = 2)
    private BigDecimal alertHrLower;

    @Column(name = "alert_spo2_range", precision = 5, scale = 2)
    private BigDecimal alertSpo2Range;

    @Column(name = "samples_count", nullable = false)
    private Integer samplesCount = 0;

    @Column(name = "last_learned_at")
    private LocalDateTime lastLearnedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
