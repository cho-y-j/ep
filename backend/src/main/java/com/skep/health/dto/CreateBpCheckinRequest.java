package com.skep.health.dto;

import java.time.LocalDateTime;

/**
 * 혈압 체크인 입력(관리자·BP·공급사 대행). verdict 는 서버 계산이라 요청에 없음.
 * measuredAt 미지정 시 서버 현재시각. method 미지정 시 MANUAL.
 */
public record CreateBpCheckinRequest(
        Long personId,
        Long siteId,
        Integer sys,
        Integer dia,
        Integer pulse,
        String method,
        LocalDateTime measuredAt
) {
}
