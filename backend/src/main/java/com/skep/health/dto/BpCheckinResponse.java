package com.skep.health.dto;

import com.skep.health.BpCheckin;

import java.time.LocalDateTime;

/** 혈압 체크인 응답 — 판정(verdict) + 조회용 인원/현장 명칭. */
public record BpCheckinResponse(
        Long id,
        Long personId,
        String personName,
        Long siteId,
        String siteName,
        Integer sys,
        Integer dia,
        Integer pulse,
        String method,
        String verdict,
        LocalDateTime measuredAt,
        LocalDateTime createdAt
) {
    public static BpCheckinResponse of(BpCheckin c, String personName, String siteName) {
        return new BpCheckinResponse(
                c.getId(), c.getPersonId(), personName, c.getSiteId(), siteName,
                c.getSys(), c.getDia(), c.getPulse(), c.getMethod(),
                c.getVerdict().name(), c.getMeasuredAt(), c.getCreatedAt());
    }
}
