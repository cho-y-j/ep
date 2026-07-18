package com.skep.quotetemplate.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/** 견적 템플릿 생성·수정 공용 요청. rows 는 라인 배열(패스스루). */
public record SaveQuoteTemplateRequest(
        @NotBlank String name,
        String memo,
        List<Map<String, Object>> rows
) {}
