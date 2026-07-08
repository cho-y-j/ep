package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_sensor_readings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class FieldSensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "work_plan_id")
    private Long workPlanId;

    @Column(name = "site_id")
    private Long siteId;

    private Integer hr;
    private Integer spo2;

    @Column(name = "body_temp", precision = 4, scale = 1)
    private BigDecimal bodyTemp;

    private Integer stress;

    @Column(length = 32)
    private String state;

    private Double lat;
    private Double lng;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }
}
