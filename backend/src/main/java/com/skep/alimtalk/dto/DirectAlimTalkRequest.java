package com.skep.alimtalk.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 독립 알림톡 직접 발송 요청.
 * template = AlimTalkTemplate enum 이름(HEALTH_CHECK / VEHICLE_SAFETY / EQUIPMENT_QUOTE).
 * vars = #{변수}=값 (브랜드명/요청일시는 서버가 자동 채움). phones = 수신번호 목록.
 */
public record DirectAlimTalkRequest(
        @NotNull String template,
        Map<String, String> vars,
        @NotEmpty List<String> phones
) {}
