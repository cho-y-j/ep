package com.example.verifyapi.rims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * RIMS API 암호화 유틸리티
 * - AES-256-ECB-PKCS5Padding 방식으로 암호화 (RIMS 공식 규약)
 * - 암호화 결과를 Base64 인코딩하여 반환
 */
@Component
public class RimsCrypto {

    private static final Logger log = LoggerFactory.getLogger(RimsCrypto.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int AES_KEY_LENGTH = 256; // AES-256 (32 bytes)

    private final RimsProperties properties;

    public RimsCrypto(RimsProperties properties) {
        this.properties = properties;
    }

    /**
     * 평문 JSON을 AES-256-ECB로 암호화 후 Base64 인코딩
     *
     * @param plainText 암호화할 평문 (JSON 문자열)
     * @return Base64 인코딩된 암호문
     * @throws RimsCryptoException 암호화 실패 시
     */
    public String encrypt(String plainText) {
        try {
            byte[] keyBytes = resolveKeyBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (RimsCryptoException e) {
            throw e;
        } catch (Exception e) {
            log.error("RIMS encryption failed", e);
            throw new RimsCryptoException("Encryption failed", e);
        }
    }

    /**
     * Base64 인코딩된 암호문을 복호화
     *
     * @param encryptedText Base64 인코딩된 암호문
     * @return 복호화된 평문
     * @throws RimsCryptoException 복호화 실패 시
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] keyBytes = resolveKeyBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (RimsCryptoException e) {
            throw e;
        } catch (Exception e) {
            log.error("RIMS decryption failed", e);
            throw new RimsCryptoException("Decryption failed", e);
        }
    }

    /**
     * secretKey를 32바이트 AES-256 키로 변환 (RIMS 공식 규약)
     * - Arrays.copyOf로 32바이트로 맞춤 (부족하면 0x00 패딩, 넘으면 자름)
     */
    private byte[] resolveKeyBytes() {
        String secretKey = properties.getSecretKey();
        if (secretKey == null || secretKey.isBlank()) {
            log.error("RIMS_SECRET_KEY is not configured");
            throw new RimsCryptoException("RIMS_SECRET_KEY is not configured");
        }

        // trim 적용 (앞뒤 공백 제거)
        secretKey = secretKey.trim();

        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);

        // 키 길이 로그 (값은 절대 로그에 찍지 않음)
        log.debug("RIMS_SECRET_KEY byte length: {}", keyBytes.length);

        // RIMS 규약: AES-256 (32 bytes)로 맞춤
        return Arrays.copyOf(keyBytes, AES_KEY_LENGTH / 8);
    }

    /**
     * RIMS 암호화 관련 예외
     */
    public static class RimsCryptoException extends RuntimeException {
        public RimsCryptoException(String message) {
            super(message);
        }

        public RimsCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
