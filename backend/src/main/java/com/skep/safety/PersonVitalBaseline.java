package com.skep.safety;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P5-W1 개인 심박 대역(서버 2차 판정용) + 학습 보정 상태. 1인 1행.
 * 대역은 서버가 raw readings(정상 상태) 로 재학습(drift 추종), adjust_pct 는 자가취소/실제사건 피드백으로 보정.
 * field_baselines(워치 온디바이스 EMA)와 별개 — 이 표는 서버 판정·보정 전용.
 */
@Entity
@Table(name = "person_vital_baselines")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class PersonVitalBaseline {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "rest_hr_low")
    private Integer restHrLow;

    @Column(name = "rest_hr_high")
    private Integer restHrHigh;

    @Column(name = "work_hr_low")
    private Integer workHrLow;

    /** 작업 심박 상한(p90) — 지속 고심박 판정 기준. adjust_pct 반영 전 원본. */
    @Column(name = "work_hr_high")
    private Integer workHrHigh;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount = 0;

    @Column(name = "learned_at")
    private LocalDateTime learnedAt;

    /** 개인 보정 %(-10 ~ +20). +=완화(임계 상향), -=강화(임계 하향). */
    @Column(name = "adjust_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal adjustPct = BigDecimal.ZERO;

    /** 오탐(자가취소) 누적. */
    @Column(name = "fp_count", nullable = false)
    private Integer fpCount = 0;

    /** 실제사건(관리자 확인) 누적. */
    @Column(name = "tp_count", nullable = false)
    private Integer tpCount = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 대역이 산출됐는가(서버 2차 판정·스파크라인 밴드 사용 가능). */
    public boolean isLearned() {
        return workHrHigh != null && learnedAt != null;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
