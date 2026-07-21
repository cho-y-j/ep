package com.skep.document.dto;

import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeService;
import com.skep.document.OwnerType;

public record DocumentTypeResponse(
        Long id,
        String name,
        OwnerType appliesTo,
        boolean hasExpiry,
        boolean requiresVerification,
        int sortOrder,
        boolean active,
        // V14 정책/검증 필드
        boolean required,
        boolean blocksAssignment,
        Integer defaultValidMonths,
        boolean ocrEnabled,
        String ocrExtractType,
        String ocrExpiryFieldKey,
        String verifyEndpoint,
        String requiredFields,          // JSON 문자열 — 프론트는 JSON.parse 필요
        // 역할/카테고리 매칭 — 서류 등록 시 필수/선택/기타 그룹핑에 사용 (읽기 전용)
        String appliesToPersonRoles,    // PersonRole CSV, null = 모든 역할
        String appliesToCategories,     // EquipmentCategory CSV, null = 모든 카테고리
        String ocrRegionTemplate,       // 영역-크롭 OCR 템플릿 JSON, null = 미사용 (FE 분기 기준)
        String sampleImageUrl,          // V116: '샘플 보기' 예시 이미지 URL, null = 미등록
        String sampleDescription        // V119: '샘플 보기' 설명글, null = 미등록
) {
    public static DocumentTypeResponse from(DocumentType t) {
        return new DocumentTypeResponse(
                t.getId(), t.getName(), t.getAppliesTo(),
                t.isHasExpiry(), t.isRequiresVerification(),
                t.getSortOrder(), t.isActive(),
                t.isRequired(), t.isBlocksAssignment(), t.getDefaultValidMonths(),
                t.isOcrEnabled(), t.getOcrExtractType(), t.getOcrExpiryFieldKey(),
                t.getVerifyEndpoint(), t.getRequiredFields(),
                t.getAppliesToPersonRoles(), t.getAppliesToCategories(),
                t.getOcrRegionTemplate(),
                DocumentTypeService.sampleImageUrl(t),
                t.getSampleDescription()
        );
    }
}
