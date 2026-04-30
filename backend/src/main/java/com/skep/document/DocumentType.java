package com.skep.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_types")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 16)
    private OwnerType appliesTo;

    @Column(name = "has_expiry", nullable = false)
    private boolean hasExpiry;

    @Column(name = "requires_verification", nullable = false)
    private boolean requiresVerification;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DocumentType(String name, OwnerType appliesTo, boolean hasExpiry,
                         boolean requiresVerification, Integer sortOrder, Boolean active) {
        this.name = name;
        this.appliesTo = appliesTo;
        this.hasExpiry = hasExpiry;
        this.requiresVerification = requiresVerification;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.active = active == null || active;
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

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}
