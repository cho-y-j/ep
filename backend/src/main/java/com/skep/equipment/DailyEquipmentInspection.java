package com.skep.equipment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 차량/장비 일상점검(시업점검) — 조종원이 매일 아침 제출. items 는 체크리스트 JSON 문자열. */
@Entity
@Table(name = "daily_equipment_inspections")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DailyEquipmentInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "inspected_by_person_id")
    private Long inspectedByPersonId;

    @Column(name = "inspect_date", nullable = false)
    private LocalDate inspectDate;

    /** 체크리스트 JSON: [{key,label,result,note}]. */
    @Column(columnDefinition = "TEXT")
    private String items;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** PASS | ATTENTION | FAIL */
    @Column(length = 20)
    private String overall;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
