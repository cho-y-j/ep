package com.skep.equipment.timeline;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_operation_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "site_name", length = 100)
    private String siteName;

    @Column(length = 255)
    private String description;

    @Column(name = "utilization_pct")
    private Integer utilizationPct;

    @Column(nullable = false, length = 32)
    private String status;     // RUNNING | DONE | IDLE | BROKEN

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
