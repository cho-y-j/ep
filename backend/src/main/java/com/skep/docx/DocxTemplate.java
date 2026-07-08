package com.skep.docx;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DOCX 출력 템플릿. target_type 별로 placeholder 의미가 다름. company_id NULL = 전역.
 */
@Entity
@Table(name = "docx_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocxTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;     // 현재는 "WORK_PLAN" 만 지원

    @Column(name = "company_id")
    private Long companyId;        // NULL = 전역

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "file_key", nullable = false, length = 255)
    private String fileKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private DocxTemplate(String targetType, Long companyId, String name, String fileKey,
                         Long fileSize, Long uploadedBy) {
        this.targetType = targetType;
        this.companyId = companyId;
        this.name = name;
        this.fileKey = fileKey;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
    }

    public void rename(String newName) { if (newName != null) this.name = newName; }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
