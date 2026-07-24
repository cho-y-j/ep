package com.skep.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 발송 메일 계정의 앱 비밀번호 등 민감 크리덴셜을 DB 저장용으로 암/복호화한다.
 * AES-256-GCM(기밀성+무결성). 키는 env {@code MAIL_CRED_KEY}(임의 문자열)를 SHA-256 으로 유도한다.
 * 키 미설정 시 기동은 되지만 경고를 남기고, 저장 API 는 400·복호화는 예외로 처리한다(조용한 평문 저장 금지).
 * 저장 형식: base64( IV(12B) || ciphertext+tag ).
 */
@Component
public class MailCredentialCipher {

    private static final Logger log = LoggerFactory.getLogger(MailCredentialCipher.class);
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    /** null 이면 키 미설정 — 암/복호화 불가. */
    private final SecretKeySpec key;

    public MailCredentialCipher(@Value("${MAIL_CRED_KEY:}") String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            this.key = null;
            log.warn("MAIL_CRED_KEY 미설정 — 발송 메일 계정 비밀번호 저장/사용 불가. 운영 배포 전 반드시 설정하세요.");
        } else {
            this.key = new SecretKeySpec(sha256(rawKey), "AES");
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    /** 평문 → 저장문자열(base64). 키 미설정 시 IllegalStateException. */
    public String encrypt(String plaintext) {
        if (key == null) throw new IllegalStateException("MAIL_CRED_KEY 미설정");
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("메일 크리덴셜 암호화 실패", e);
        }
    }

    /** 저장문자열(base64) → 평문. 키 미설정/변조 시 IllegalStateException. */
    public String decrypt(String stored) {
        if (key == null) throw new IllegalStateException("MAIL_CRED_KEY 미설정");
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(all, IV_LEN, all.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("메일 크리덴셜 복호화 실패", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
