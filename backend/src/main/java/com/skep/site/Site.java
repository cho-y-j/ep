package com.skep.site;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bp_company_id", nullable = false)
    private Long bpCompanyId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 64)
    private String code;

    @Column(length = 255)
    private String address;

    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SiteStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "polygon_geojson", columnDefinition = "TEXT")
    private String polygonGeojson;

    @Column(name = "map_zoom")
    private Integer mapZoom;

    @Column(name = "geofence_radius_m")
    private Integer geofenceRadiusM;

    /** 현장별 정산 기준일(day-of-month 1~31). NULL=미지정(정산 조회 시 말일 취급). BP/ADMIN 이 지정. */
    @Column(name = "settlement_day")
    private Integer settlementDay;

    @Builder
    private Site(Long bpCompanyId, String name, String code, String address, String detailAddress,
                 LocalDate startDate, LocalDate endDate, SiteStatus status, Long createdBy) {
        this.bpCompanyId = bpCompanyId;
        this.name = name;
        this.code = code;
        this.address = address;
        this.detailAddress = detailAddress;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status != null ? status : SiteStatus.ACTIVE;
        this.createdBy = createdBy;
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

    public void update(String name, String code, String address, String detailAddress,
                       LocalDate startDate, LocalDate endDate, SiteStatus status) {
        if (name != null) this.name = name;
        this.code = code;
        this.address = address;
        this.detailAddress = detailAddress;
        this.startDate = startDate;
        this.endDate = endDate;
        if (status != null) this.status = status;
    }

    public void updateMap(Double latitude, Double longitude, String polygonGeojson, Integer mapZoom) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.polygonGeojson = polygonGeojson;
        this.mapZoom = mapZoom;
    }

    /** 현장 정산 기준일 지정/해제(NULL). */
    public void updateSettlementDay(Integer settlementDay) {
        this.settlementDay = settlementDay;
    }
}
