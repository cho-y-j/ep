package com.example.verifyapi.rims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RIMS OAuth2 토큰 관리 서비스
 * - 토큰 발급 및 캐싱 (3시간 유효)
 * - 만료 시 자동 재발급
 * - 동시성 제어 (중복 발급 방지)
 */
@Service
public class RimsTokenService {

    private static final Logger log = LoggerFactory.getLogger(RimsTokenService.class);
    private static final String TOKEN_ENDPOINT = "/col/oauth2";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BASIC_PREFIX = "Basic ";

    private final RimsProperties properties;
    private final RestTemplate restTemplate;

    // 토큰 캐싱
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt;

    // 동시성 제어를 위한 락
    private final ReentrantLock tokenLock = new ReentrantLock();

    public RimsTokenService(RimsProperties properties,
                            @Qualifier("rimsRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 유효한 Bearer 토큰 반환
     * - 캐시된 토큰이 유효하면 반환
     * - 만료되었거나 없으면 새로 발급
     *
     * @return Bearer 토큰 (prefix 없이)
     * @throws RimsTokenException 토큰 발급 실패 시
     */
    public String getToken() {
        // 캐시된 토큰이 유효한지 확인 (만료 1분 전부터 갱신)
        if (isTokenValid()) {
            return cachedToken;
        }

        return refreshToken();
    }

    /**
     * 토큰 강제 재발급
     * 401 응답 등으로 토큰이 무효화된 경우 호출
     *
     * @return 새로 발급된 Bearer 토큰
     * @throws RimsTokenException 토큰 발급 실패 시
     */
    public String refreshToken() {
        tokenLock.lock();
        try {
            // 다른 스레드가 이미 갱신했는지 재확인 (Double-check locking)
            if (isTokenValid()) {
                return cachedToken;
            }

            log.info("Requesting new RIMS OAuth2 token");
            String newToken = requestNewToken();

            cachedToken = newToken;
            tokenExpiresAt = Instant.now().plusSeconds(properties.getTokenTtlSeconds());

            log.info("RIMS token refreshed, expires at: {}", tokenExpiresAt);
            return newToken;

        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * 토큰 캐시 무효화
     */
    public void invalidateToken() {
        tokenLock.lock();
        try {
            cachedToken = null;
            tokenExpiresAt = null;
            log.info("RIMS token cache invalidated");
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean isTokenValid() {
        if (cachedToken == null || tokenExpiresAt == null) {
            return false;
        }
        // 만료 1분 전부터 갱신 대상으로 처리
        return Instant.now().plusSeconds(60).isBefore(tokenExpiresAt);
    }

    private String requestNewToken() {
        String baseUrl = properties.getBaseUrl();
        String authKey = properties.getAuthKey();

        if (baseUrl == null || baseUrl.isBlank() || authKey == null || authKey.isBlank()) {
            throw new RimsTokenException("RIMS baseUrl and authKey must be configured");
        }

        // baseUrl 끝의 '/' 제거
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String url = baseUrl + TOKEN_ENDPOINT + "?grantType=password";

        HttpHeaders headers = new HttpHeaders();
        String basicAuth = BASIC_PREFIX + Base64.getEncoder()
                .encodeToString(authKey.getBytes(StandardCharsets.UTF_8));
        headers.set(AUTHORIZATION_HEADER, basicAuth);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Void.class
            );

            String authHeader = response.getHeaders().getFirst(AUTHORIZATION_HEADER);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.error("RIMS token response missing Authorization header");
                throw new RimsTokenException("Invalid token response: missing Authorization header");
            }

            return authHeader.substring(BEARER_PREFIX.length());

        } catch (RestClientException e) {
            log.error("RIMS token request failed", e);
            throw new RimsTokenException("Token request failed", e);
        }
    }

    /**
     * RIMS 토큰 관련 예외
     */
    public static class RimsTokenException extends RuntimeException {
        public RimsTokenException(String message) {
            super(message);
        }

        public RimsTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
