package com.skep.document.dto;

import java.util.List;

/**
 * 자원(장비/인원)을 골라 서류를 자원별 zip 으로 묶어 검토 발송.
 * emails 로 이메일 발송 + bpCompanyId 가 있으면 그 BP사 계정 수신함에도 등록(둘 중 하나 이상 필요).
 */
public record SendDocumentReviewMailRequest(
        List<String> emails,
        List<Long> equipmentIds,
        List<Long> personIds,
        String message,
        Long bpCompanyId
) {
}
