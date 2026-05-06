package com.skep.equipment.timeline;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_inspection_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "inspected_at", nullable = false)
    private LocalDate inspectedAt;

    @Column(length = 100)
    private String inspector;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 32)
    private String result;     // PASS | ATTENTION | FAIL

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "next_inspection_at")
    private LocalDate nextInspectionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
