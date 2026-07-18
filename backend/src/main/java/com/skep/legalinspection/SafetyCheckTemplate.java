package com.skep.legalinspection;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S2′ 법정점검 템플릿 — ADMIN 이 항목을 편집(어드민 편집형). v1 target=EQUIPMENT 고정.
 * items = 점검 항목 배열(JSONB 패스스루): [{no, text, required}].
 */
@Entity
@Table(name = "safety_check_templates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyCheckTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 32)
    private String target = "EQUIPMENT";

    @Type(JsonBinaryType.class)
    @Column(name = "items", columnDefinition = "jsonb")
    private List<Map<String, Object>> items = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static SafetyCheckTemplate create(Long createdBy) {
        SafetyCheckTemplate t = new SafetyCheckTemplate();
        t.createdBy = createdBy;
        t.createdAt = LocalDateTime.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
