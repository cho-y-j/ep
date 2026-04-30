package com.skep.person;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "persons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(nullable = false, length = 100)
    private String name;

    private LocalDate birth;

    @Column(length = 32)
    private String phone;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(name = "photo_content_type", length = 100)
    private String photoContentType;

    @ElementCollection(fetch = FetchType.EAGER, targetClass = PersonRole.class)
    @CollectionTable(name = "person_roles", joinColumns = @JoinColumn(name = "person_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 32)
    private Set<PersonRole> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Person(Long supplierId, String name, LocalDate birth, String phone, Set<PersonRole> roles) {
        this.supplierId = supplierId;
        this.name = name;
        this.birth = birth;
        this.phone = phone;
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
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

    public void update(String name, LocalDate birth, String phone, Set<PersonRole> roles) {
        if (name != null) this.name = name;
        if (birth != null) this.birth = birth;
        if (phone != null) this.phone = phone;
        if (roles != null) {
            this.roles.clear();
            this.roles.addAll(roles);
        }
    }

    public void setPhoto(String key, String contentType) {
        this.photoKey = key;
        this.photoContentType = contentType;
    }

    public void clearPhoto() {
        this.photoKey = null;
        this.photoContentType = null;
    }
}
