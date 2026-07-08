package com.skep.equipment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * V36: 장비별 기본 조종원. 우선순위 1 = 1순위.
 * 장비공급사가 미리 등록해두면 견적/작업계획서 prefill 에 자동 사용.
 */
@Entity
@Table(name = "equipment_default_operators",
        uniqueConstraints = @UniqueConstraint(columnNames = {"equipment_id", "person_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentDefaultOperator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public EquipmentDefaultOperator(Long equipmentId, Long personId, Integer priority) {
        this.equipmentId = equipmentId;
        this.personId = personId;
        this.priority = priority != null ? priority : 1;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }

    public void updatePriority(Integer priority) {
        this.priority = priority != null ? priority : 1;
    }
}
