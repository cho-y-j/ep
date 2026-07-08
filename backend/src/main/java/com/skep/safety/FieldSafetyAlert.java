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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
