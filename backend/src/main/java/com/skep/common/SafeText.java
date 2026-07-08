package com.skep.common;

/**
 * 사용자 입력을 audit JSON / 메일 헤더 / 파일명에 안전하게 넣기 위한 공용 헬퍼.
 * 서비스마다 사본으로 존재하던 구현을 한 곳으로 모음 (보안 로직은 단일 구현 유지).
 */
public final class SafeText {

    private SafeText() {}

    /** audit 로그용 JSON 문자열 값 이스케이프. */
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /** 메일 헤더에 들어갈 이메일 검증 — CRLF/제어문자/@ 누락 차단 (헤더 인젝션 방어). */
    public static boolean isSafeEmail(String email) {
        if (email == null || email.isBlank()) return false;
        if (email.indexOf('\r') >= 0 || email.indexOf('\n') >= 0) return false;
        if (email.indexOf('\0') >= 0) return false;
        return email.contains("@");
    }

    /** 파일명 안전화 — 경로구분/제어문자/CRLF 를 '_' 로. 빈 입력은 "file". */
    public static String sanitizeFileName(String s) {
        if (s == null || s.isBlank()) return "file";
        return s.replaceAll("[\\r\\n\\u0000-\\u001F/\\\\:*?\"<>|]", "_").trim();
    }
}
