package com.skep.person;

import java.security.SecureRandom;

/** 출퇴근 코드 생성기. 0/O, 1/I/L, B/8 같은 헷갈리는 문자 제외. */
public final class AttendanceCodeGenerator {
    private static final char[] CHARS = "ACDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RAND = new SecureRandom();
    private static final int LENGTH = 6;

    private AttendanceCodeGenerator() {}

    public static String next() {
        var sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) sb.append(CHARS[RAND.nextInt(CHARS.length)]);
        return sb.toString();
    }
}
