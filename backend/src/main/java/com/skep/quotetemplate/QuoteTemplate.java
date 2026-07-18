package com.skep.quotetemplate;

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
 * 견적 템플릿(단가표) — 공급사가 미리 등록(§3.3). 발송 화면에서 불러와 발송 내용에 삽입.
 * rows = 라인 N행. 백엔드는 해석하지 않는 JSONB 패스스루(프론트가 행 모양 정의):
 * [{equipment_desc, rate_type(DAILY|MONTHLY), base_rate, rate_early/lunch/evening/night/overnight, note}].
 */
@Entity
@Table(name = "quote_templates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_company_id", nullable = false)
    private Long supplierCompanyId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String memo;

    @Type(JsonBinaryType.class)
    @Column(name = "rows", columnDefinition = "jsonb")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static QuoteTemplate create(Long supplierCompanyId, Long createdBy) {
        QuoteTemplate t = new QuoteTemplate();
        t.supplierCompanyId = supplierCompanyId;
        t.createdBy = createdBy;
        t.createdAt = LocalDateTime.now();
        t.updatedAt = t.createdAt;
        return t;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
