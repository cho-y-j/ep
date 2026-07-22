package com.skep.document.dto;

import java.util.List;

/**
 * 장비+교대조 조종원 서류를 장비별 병합 PDF로 묶어 발송.
 * bundles 각 항목 = 장비 1대 + 포함할 조종원(operatorPersonIds). emails 로 이메일 발송(묶음마다 PDF 첨부) +
 * bpCompanyId 가 있으면 그 BP사 계정 수신함에도 봉투 등록(둘 중 하나 이상 필요). JSON 전역 SNAKE_CASE.
 */
public record ReviewBundlePdfRequest(
        List<Bundle> bundles,
        List<String> emails,
        Long bpCompanyId,
        String message,
        Boolean separatorPage
) {
    public record Bundle(Long equipmentId, List<Long> operatorPersonIds) {}
}
