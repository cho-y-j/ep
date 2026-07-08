package com.skep.workplan;

import com.skep.document.OwnerType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 작업계획서 자원 추가 시점의 서류 컴플라이언스 스냅샷.
 *
 * 같은 자원이 같은 plan 에 여러 번 추가/제거될 수 있으니 history 형태로 누적.
 * status = OK | WARNING | BLOCKED | OVERRIDDEN.
 */
@Entity
@Table(name = "work_plan_compliance_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPlanComplianceCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_plan_id", nullable = false)
    private Long workPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private OwnerType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComplianceStatus status;

    @Column(length = 255)
    private String reason;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private LocalDateTime checkedAt;

    @Column(name = "override_by")
    private Long overrideBy;

    @Column(name = "override_reason", length = 255)
    private String overrideReason;

    @Builder
    private WorkPlanComplianceCheck(Long workPlanId, OwnerType targetType, Long targetId,
                                    ComplianceStatus status, String reason,
                                    Long overrideBy, String overrideReason) {
        this.workPlanId = workPlanId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.status = status;
        this.reason = reason;
        this.overrideBy = overrideBy;
        this.overrideReason = overrideReason;
    }

    @PrePersist
    void onCreate() { this.checkedAt = LocalDateTime.now(); }
}
