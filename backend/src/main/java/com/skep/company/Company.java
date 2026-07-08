package com.skep.company;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "business_number", nullable = false, unique = true, length = 32)
    private String businessNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyType type;

    @Column(name = "business_address", length = 255)
    private String businessAddress;

    @Column(name = "business_category", length = 100)
    private String businessCategory;

    @Column(name = "business_subcategory", length = 200)
    private String businessSubcategory;

    @Column(name = "ceo_name", length = 100)
    private String ceoName;

    @Column(length = 32)
    private String phone;

    @Column(length = 32)
    private String fax;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Company(String name, String businessNumber, CompanyType type) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.type = type;
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

    public void rename(String newName) {
        this.name = newName;
    }

    public void updateProfile(String businessAddress, String businessCategory, String businessSubcategory,
                              String ceoName, String phone, String fax) {
        this.businessAddress = businessAddress;
        this.businessCategory = businessCategory;
        this.businessSubcategory = businessSubcategory;
        this.ceoName = ceoName;
        this.phone = phone;
        this.fax = fax;
    }
}
