package com.skep.quotetemplate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteTemplateRepository extends JpaRepository<QuoteTemplate, Long> {

    /** 공급사 자기 회사 템플릿(최근순). */
    List<QuoteTemplate> findBySupplierCompanyIdOrderByIdDesc(Long supplierCompanyId);
}
