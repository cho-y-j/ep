package com.skep.equipment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장비 종류(차종) 마스터. ADMIN 이 추가/수정/숨김 관리.
 * 기존 EquipmentCategory enum 을 대체한 정본 — code 는 옛 enum name 과 동일 문자열.
 * code 는 불변 식별자(equipment.category·applies_to_categories 가 참조) — 라벨/그룹/순서/활성만 수정.
 */
@Entity
@Table(name = "equipment_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentType {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 16)
    private String grp;   // 건설기계 / 차량 / 기타

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EquipmentType(String code, String name, String grp, int sortOrder) {
        this.code = code;
        this.name = name;
        this.grp = grp;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, String grp, Integer sortOrder, Boolean active) {
        if (name != null) this.name = name;
        if (grp != null) this.grp = grp;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (active != null) this.active = active;
    }
}
