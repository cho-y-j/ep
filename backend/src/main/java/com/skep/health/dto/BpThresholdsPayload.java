package com.skep.health.dto;

import com.skep.health.BpThresholds;

/** 현장 혈압 임계 조회/저장 공용 페이로드(GET 응답 = PUT 요청). configured=저장 행 존재 여부(GET 만 의미). */
public record BpThresholdsPayload(
        Long siteId,
        boolean configured,
        Integer cautionSys,
        Integer cautionDia,
        Integer blockSys,
        Integer blockDia
) {
    public static BpThresholdsPayload of(Long siteId, boolean configured, BpThresholds t) {
        return new BpThresholdsPayload(siteId, configured,
                t.cautionSys(), t.cautionDia(), t.blockSys(), t.blockDia());
    }
}
