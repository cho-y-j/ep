package com.example.verifyapi.rims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * RIMS API 호출 클라이언트
 * - Bearer 토큰 자동 주입
 * - 요청 데이터 암호화 (encryptedData)
 * - 401 응답 시 토큰 재발급 후 1회 재시도
 */
@Component
public class RimsClient {

    private static final Logger log = LoggerFactory.getLogger(RimsClient.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RimsProperties properties;
    private final RimsTokenService tokenService;
    private final RimsCrypto crypto;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RimsClient(RimsProperties properties,
                      RimsTokenService tokenService,
                      RimsCrypto crypto,
                      @Qualifier("rimsRestTemplate") RestTemplate restTemplate,
                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.crypto = crypto;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * RIMS API POST 호출
     * - 요청 데이터를 암호화하여 전송
     * - 401 응답 시 토큰 재발급 후 1회 재시도
     *
     * @param endpoint API 엔드포인트 (예: /dapi/getLcncConf)
     * @param requestData 요청 데이터 (암호화 전 평문 객체)
     * @return 응답 JSON 문자열
     * @throws RimsClientException 호출 실패 시
     */
    public String post(String endpoint, Object requestData) {
        return postWithRetry(endpoint, requestData, true);
    }

    private String postWithRetry(String endpoint, Object requestData, boolean retryOn401) {
        String url = buildUrl(endpoint);
        String token = tokenService.getToken();

        // 요청 데이터 암호화
        String encryptedData = encryptRequest(requestData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AUTHORIZATION_HEADER, BEARER_PREFIX + token);

        // encryptedData를 JSON body로 구성
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of("encryptedData", encryptedData));
        } catch (JsonProcessingException e) {
            throw new RimsClientException("RIMS_SERIALIZATION_ERROR", "Failed to build request body", e);
        }
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.debug("RIMS API call: POST {}", url);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            String body = response.getBody();
            log.info("RIMS API response (status={}): {}", response.getStatusCode(), body);
            return body;

        } catch (HttpClientErrorException.Unauthorized e) {
            if (retryOn401) {
                log.warn("RIMS API returned 401, refreshing token and retrying");
                tokenService.invalidateToken();
                return postWithRetry(endpoint, requestData, false);
            }
            log.error("RIMS API 401 after token refresh", e);
            throw new RimsClientException("RIMS_AUTH_FAILED", "Authentication failed after token refresh", e);

        } catch (HttpClientErrorException e) {
            log.error("RIMS API client error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RimsClientException("RIMS_CLIENT_ERROR",
                    "Client error: " + e.getStatusCode(), e);

        } catch (RestClientException e) {
            log.error("RIMS API call failed", e);
            throw new RimsClientException("RIMS_CONNECTION_ERROR", "Connection failed", e);
        }
    }

    private String buildUrl(String endpoint) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new RimsClientException("RIMS_NOT_CONFIGURED", "RIMS baseUrl is not configured");
        }

        // baseUrl 끝의 '/' 제거
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String servicePath = properties.getServicePath();
        if (servicePath != null && !servicePath.isBlank()) {
            // servicePath 앞에 '/' 보장
            if (!servicePath.startsWith("/")) {
                servicePath = "/" + servicePath;
            }
            // servicePath 끝의 '/' 제거
            if (servicePath.endsWith("/")) {
                servicePath = servicePath.substring(0, servicePath.length() - 1);
            }
            baseUrl = baseUrl + servicePath;
        }

        // endpoint 앞에 '/' 보장
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }

        return baseUrl + endpoint;
    }

    private String encryptRequest(Object requestData) {
        try {
            String json = objectMapper.writeValueAsString(requestData);
            log.info("RIMS encrypting JSON: {}", json);
            return crypto.encrypt(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request data", e);
            throw new RimsClientException("RIMS_SERIALIZATION_ERROR", "Failed to serialize request", e);
        } catch (RimsCrypto.RimsCryptoException e) {
            throw new RimsClientException("RIMS_ENCRYPTION_ERROR", "Failed to encrypt request", e);
        }
    }

    /**
     * RIMS API 응답 파싱
     *
     * @param responseBody 응답 JSON 문자열
     * @return 파싱된 Map
     * @throws RimsClientException 파싱 실패 시
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse RIMS response (length={})",
                    responseBody != null ? responseBody.length() : 0, e);
            throw new RimsClientException("RIMS_PARSE_ERROR", "Failed to parse response", e);
        }
    }

    /**
     * RIMS 클라이언트 예외
     */
    public static class RimsClientException extends RuntimeException {
        private final String reasonCode;

        public RimsClientException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        public RimsClientException(String reasonCode, String message, Throwable cause) {
            super(message, cause);
            this.reasonCode = reasonCode;
        }

        public String getReasonCode() {
            return reasonCode;
        }
    }
}
