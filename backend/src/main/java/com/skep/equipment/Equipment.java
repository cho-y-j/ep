package com.skep.equipment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "vehicle_no", length = 32)
    private String vehicleNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EquipmentCategory category;

    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String manufacturer;

    private Integer year;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Equipment(Long supplierId, String vehicleNo, EquipmentCategory category,
                      String model, String manufacturer, Integer year) {
        this.supplierId = supplierId;
        this.vehicleNo = vehicleNo;
        this.category = category;
        this.model = model;
        this.manufacturer = manufacturer;
        this.year = year;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String vehicleNo, EquipmentCategory category, String model, String manufacturer, Integer year) {
        if (vehicleNo != null) this.vehicleNo = vehicleNo;
        if (category != null) this.category = category;
        if (model != null) this.model = model;
        if (manufacturer != null) this.manufacturer = manufacturer;
        if (year != null) this.year = year;
    }
}
