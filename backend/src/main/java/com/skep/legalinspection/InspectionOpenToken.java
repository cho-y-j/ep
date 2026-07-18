package com.skep.legalinspection;

import com.skep.common.ApiException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * 체크리스트 오픈 토큰 — NFC 태그(장비 nfcTagId 서버 검증) 성공 시 발급, 제출 시 재검증.
 * 위조 방지: HMAC 서명(부팅 시 랜덤 키) + 단기 만료(15분). 상태 없음(무DB).
 * payload = equipmentId|inspectorPersonId|templateId|verified(1/0)|tagId|readAtMillis, "." + HMAC.
 * readAt(태그 시각)은 토큰에 담겨 제출 시 증거로 그대로 저장된다.
 */
@Component
public class InspectionOpenToken {

    /** 태그 후 제출 유효시간 — 이 시간 내에 체크리스트를 제출해야 한다. */
    static final long TTL_MINUTES = 15;

    private final SecretKeySpec key;

    public InspectionOpenToken() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String issue(Long equipmentId, Long inspectorPersonId, Long templateId,
                        boolean verified, String tagId, LocalDateTime readAt) {
        long readMillis = readAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String payload = equipmentId + "|" + inspectorPersonId + "|" + templateId + "|"
                + (verified ? "1" : "0") + "|" + b64(tagId == null ? "" : tagId) + "|" + readMillis;
        String enc = b64(payload);
        return enc + "." + sign(enc);
    }

    /** 서명·만료 검증 후 payload 반환. 실패 시 400/403. */
    public Payload verify(String token) {
        if (token == null || !token.contains(".")) {
            throw ApiException.badRequest("BAD_OPEN_TOKEN", "오픈 토큰이 올바르지 않습니다");
        }
        int dot = token.lastIndexOf('.');
        String enc = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!constantTimeEquals(sign(enc), sig)) {
            throw ApiException.forbidden("OPEN_TOKEN_INVALID", "오픈 토큰 서명이 유효하지 않습니다");
        }
        String[] parts = decode(enc).split("\\|", -1);
        if (parts.length != 6) {
            throw ApiException.badRequest("BAD_OPEN_TOKEN", "오픈 토큰 형식 오류");
        }
        long readMillis = Long.parseLong(parts[5]);
        LocalDateTime readAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(readMillis), ZoneId.systemDefault());
        if (readAt.plusMinutes(TTL_MINUTES).isBefore(LocalDateTime.now())) {
            throw ApiException.forbidden("OPEN_TOKEN_EXPIRED", "오픈 토큰이 만료되었습니다. NFC 태그부터 다시 하세요");
        }
        return new Payload(Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2]),
                "1".equals(parts[3]), decode(parts[4]), readAt);
    }

    public record Payload(Long equipmentId, Long inspectorPersonId, Long templateId,
                          boolean verified, String tagId, LocalDateTime readAt) {}

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 서명 실패", e);
        }
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
