package com.skep.user.dto;

import com.skep.user.Role;
import com.skep.user.User;

import java.time.LocalDateTime;

/** 승인 대기(enabled=false) 사용자 — 회사명 포함. JSON 전역 SNAKE_CASE. */
public record PendingUserResponse(
        Long id,
        String email,
        String name,
        String phone,
        Role role,
        Long companyId,
        String companyName,
        LocalDateTime createdAt
) {
    public static PendingUserResponse from(User u, String companyName) {
        return new PendingUserResponse(
                u.getId(), u.getEmail(), u.getName(), u.getPhone(),
                u.getRole(), u.getCompanyId(), companyName, u.getCreatedAt());
    }
}
