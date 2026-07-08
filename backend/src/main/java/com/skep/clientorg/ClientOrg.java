package com.skep.clientorg;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 원청기관 (삼성/SK/현대 등). 가입 안 함, ADMIN 이 등록.
 * 자원(장비/인원)이 어느 ClientOrg 현장에 들어갔는지 이력 추적 + 라벨 표시 용도.
 */
@Entity
@Table(name = "client_orgs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientOrg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 32, unique = true)
    private String code;

    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ClientOrg(String name, String code, String note) {
        this.name = name;
        this.code = code;
        this.note = note;
        this.active = true;
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

    public void update(String name, String code, String note) {
        if (name != null) this.name = name;
        if (code != null) this.code = code;
        if (note != null) this.note = note;
    }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}
