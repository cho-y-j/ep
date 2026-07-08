package com.skep.workplan.preset;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자별 작업계획서 프리셋. slot 은 1~9. (user_id, slot) UNIQUE.
 * payload_json 은 자유로운 JSON 문자열로 보관 — 프론트에서 적용 시 파싱.
 */
@Entity
@Table(name = "work_plan_presets",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "slot"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkPlanPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Short slot;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private WorkPlanPreset(Long userId, Short slot, String name, String payloadJson) {
        this.userId = userId;
        this.slot = slot;
        this.name = name;
        this.payloadJson = payloadJson;
    }

    public void update(String name, String payloadJson) {
        if (name != null) this.name = name;
        if (payloadJson != null) this.payloadJson = payloadJson;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
