package com.skep.equipment.timeline;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_maintenance_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "maintained_at", nullable = false)
    private LocalDate maintainedAt;

    @Column(length = 100)
    private String maintainer;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Long cost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public EquipmentMaintenance(Long equipmentId, LocalDate maintainedAt, String maintainer,
                                String title, String description, Long cost) {
        this.equipmentId = equipmentId;
        this.maintainedAt = maintainedAt;
        this.maintainer = maintainer;
        this.title = title;
        this.description = description;
        this.cost = cost;
    }

    @PrePersist
    void onCreate() { this.createdAt = LocalDateTime.now(); }
}
