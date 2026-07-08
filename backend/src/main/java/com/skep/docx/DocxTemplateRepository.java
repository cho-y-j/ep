package com.skep.docx;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocxTemplateRepository extends JpaRepository<DocxTemplate, Long> {
    /** 회사 + 전역 템플릿을 모두 반환 (회사 우선, 그다음 전역, id desc). */
    @Query("""
            SELECT t FROM DocxTemplate t
            WHERE t.targetType = :targetType
              AND (:companyId IS NULL OR t.companyId IS NULL OR t.companyId = :companyId)
            ORDER BY t.companyId NULLS LAST, t.id DESC
            """)
    List<DocxTemplate> findVisibleForCompany(@Param("targetType") String targetType,
                                             @Param("companyId") Long companyId);

    /** ADMIN 전용 — 모든 템플릿. */
    List<DocxTemplate> findByTargetTypeOrderByIdDesc(String targetType);
}
