package com.example.verifyapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API 키 인증 필터
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * - /verify/** 경로에 대해 X-API-KEY 헤더 검증
 * - /verify/health는 인증 없이 접근 가능 (헬스 체크용)
 * - Fail-closed: API 키 미설정 시 503 반환 (설정으로 변경 가능)
 * - 상수 시간 비교로 타이밍 공격 방지
 */
@Component
@Order(1)
public class ApiKeyAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String HEALTH_ENDPOINT = "/verify/health";
    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
    private static final String MDC_REQUEST_ID = "requestId";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${verify.api.key:}")
    private String allowedApiKey;

    @Value("${verify.api.fail-closed:true}")
    private boolean failClosed;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();

        // /verify/** 경로가 아니면 필터 적용 안 함
        if (!requestUri.startsWith("/verify/")) {
            chain.doFilter(request, response);
            return;
        }

        // /verify/** 경로에 대해 MDC requestId 설정
        String requestId = generateRequestId();
        MDC.put(MDC_REQUEST_ID, requestId);

        try {
            // Health 엔드포인트는 인증 제외 (/verify/health 또는 /verify/health/)
            if (isHealthEndpoint(requestUri)) {
                chain.doFilter(request, response);
                return;
            }

            // API 키 설정 여부 확인
            if (allowedApiKey == null || allowedApiKey.isBlank()) {
                if (failClosed) {
                    log.error("[{}] verify.api.key가 설정되지 않음 - 운영 환경에서는 반드시 설정 필요", requestId);
                    sendErrorResponse(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "UNKNOWN", "API_KEY_NOT_CONFIGURED", "서버 API 키가 설정되지 않았습니다");
                    return;
                } else {
                    log.warn("[{}] verify.api.key 미설정 상태에서 요청 허용 (fail-closed=false)", requestId);
                    chain.doFilter(request, response);
                    return;
                }
            }

            // 클라이언트 API 키 검증
            String clientApiKey = httpRequest.getHeader(API_KEY_HEADER);
            if (clientApiKey == null || clientApiKey.isBlank()) {
                log.debug("[{}] API 키 헤더 누락: uri={}", requestId, requestUri);
                sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID", "UNAUTHORIZED", "API 키가 누락되었습니다");
                return;
            }

            // 상수 시간 비교로 타이밍 공격 방지
            if (!constantTimeEquals(clientApiKey.trim(), allowedApiKey)) {
                log.warn("[{}] 잘못된 API 키 시도: uri={}", requestId, requestUri);
                sendErrorResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID", "UNAUTHORIZED", "유효하지 않은 API 키입니다");
                return;
            }

            chain.doFilter(request, response);

        } finally {
            // MDC 정리 (스레드 풀 환경에서 누수 방지)
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /**
     * Health 엔드포인트 여부 확인
     */
    private boolean isHealthEndpoint(String requestUri) {
        return HEALTH_ENDPOINT.equals(requestUri) || (HEALTH_ENDPOINT + "/").equals(requestUri);
    }

    /**
     * 상수 시간 문자열 비교 (타이밍 공격 방지)
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * VerificationResult 형식의 에러 응답 전송 (Jackson ObjectMapper 사용)
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String result,
                                   String reasonCode, String message) throws IOException {
        String requestId = MDC.get(MDC_REQUEST_ID);
        if (requestId == null) {
            requestId = generateRequestId();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("result", result);
        body.put("reasonCode", reasonCode);
        body.put("message", message);
        body.put("verifiedAt", LocalDateTime.now().format(DATETIME_FORMAT));

        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(CONTENT_TYPE_JSON);
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * 요청 ID 생성
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
