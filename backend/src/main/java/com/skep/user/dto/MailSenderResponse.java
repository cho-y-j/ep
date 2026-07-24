package com.skep.user.dto;

import com.skep.user.User;

/**
 * 발송 메일 계정 상태 — 등록 여부·이메일·표시명만. 비밀번호(평문/암호문)는 절대 포함하지 않는다.
 * JSON 전역 SNAKE_CASE.
 */
public record MailSenderResponse(boolean configured, String email, String name) {

    public static MailSenderResponse from(User u) {
        boolean configured = u.getMailSenderEmail() != null && !u.getMailSenderEmail().isBlank();
        return new MailSenderResponse(
                configured,
                configured ? u.getMailSenderEmail() : null,
                configured ? u.getMailSenderName() : null);
    }
}
