package com.skep.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 발송 메일 계정 등록/해제. email 을 비우면 해제. email 이 있으면 password(앱 비밀번호) 필수.
 * password 는 저장 시 암호화되며 응답에는 절대 반환되지 않는다. JSON 전역 SNAKE_CASE.
 */
public record UpdateMailSenderRequest(
        @Size(max = 255) String email,
        @Size(max = 255) String password,
        @Size(max = 100) String name
) {}
