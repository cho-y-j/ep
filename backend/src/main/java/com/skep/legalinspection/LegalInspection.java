package com.skep.legalinspection;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S2′ 법정점검 기록 — 안전점검원(person)이 장비 NFC 태그 후 체크리스트+서명 제출.
 * 조종원 일일점검(DailyEquipmentInspection)과 별도 트랙. 증거사슬: tag_read_at·tag_verified·서명.
 * items_result = [{no, checked, na, note}]. UNIQUE(equipment_id, inspect_date, template_id).
 */
@Entity
@Table(name = "legal_inspections")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegalInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "inspector_person_id", nullable = false)
    private Long inspectorPersonId;

    @Column(name = "inspect_date", nullable = false)
    private LocalDate inspectDate;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "tag_id_submitted", length = 64)
    private String tagIdSubmitted;

    @Column(name = "tag_verified", nullable = false)
    private boolean tagVerified;

    @Column(name = "tag_read_at")
    private LocalDateTime tagReadAt;

    @Type(JsonBinaryType.class)
    @Column(name = "items_result", columnDefinition = "jsonb")
    private List<Map<String, Object>> itemsResult = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "sign_png", columnDefinition = "bytea")
    private byte[] signPng;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
