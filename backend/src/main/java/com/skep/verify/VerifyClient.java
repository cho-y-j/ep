package com.skep.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * verify-server(main-api) + verify-api 호출 클라이언트.
 *
 * 호출 경로:
 *   skep-v2 backend  →  main-api/api/verify/{biz, rims/license, cargo, kosha}    (정부 API 검증)
 *   skep-v2 backend  →  verify-api/verify/ocr/extract/{type}                       (OCR 추출)
 *
 * X-API-KEY 헤더 인증. 미연결 / 타임아웃 / 5xx 시 graceful fail —
 * `{ "result": "UNKNOWN", "reasonCode": "UPSTREAM_ERROR", "verified": false }` 형태로 응답.
 *
 * skep `LiftonVerifyClient.java` 의 골자를 그대로 가져와 skep-v2 도메인에 맞게 정리.
 */
@Component
public class VerifyClient {

    private static final Logger log = LoggerFactory.getLogger(VerifyClient.class);

    @Value("${verify.main-api.url:${VERIFY_MAIN_API_URL:http://main-api:8080}}")
    private String mainApiUrl;

    @Value("${verify.inner-api.url:${VERIFY_INNER_API_URL:http://verify-api:8081}}")
    private String innerApiUrl;

    @Value("${verify.api-key:${VERIFY_API_KEY:}}")
    private String apiKey;

    /** 외부 호출 비활성화 시 모든 메서드가 stub UPSTREAM_DISABLED 응답. dev/CI 환경 보호용. */
    @Value("${verify.enabled:${VERIFY_ENABLED:true}}")
    private boolean enabled;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void init() {
        // verify-api 응답에서 maskedImageBase64 / fullText 제거됨 — 일반 JSON 크기로 충분.
        this.webClient = WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        log.info("VerifyClient init: enabled={} main={} inner={} apiKey={}",
                enabled, mainApiUrl, innerApiUrl,
                (apiKey == null || apiKey.isBlank()) ? "(empty)" : "(set)");
    }

    // ─── 사업자등록 (NTS) ──────────────────────────────────
    public JsonNode verifyBusinessRegistration(String bizNo, String startDate, String ownerName) {
        return callJson(mainApiUrl + "/api/verify/biz", Map.of(
                "bizNo", safe(bizNo),
                "startDate", safe(startDate),
                "ownerName", safe(ownerName)
        ));
    }

    // ─── 운전면허 (RIMS) ───────────────────────────────────
    public JsonNode verifyDriverLicense(String licenseNo, String name, String licenseConditionCode) {
        Map<String, Object> body = new HashMap<>();
        // skep `LiftonVerifyClient` 그대로: f_license_no / f_resident_name / f_licn_con_code 필드.
        body.put("f_license_no", safe(licenseNo));
        body.put("f_resident_name", safe(name));
        body.put("f_licn_con_code", (licenseConditionCode != null && !licenseConditionCode.isBlank())
                ? licenseConditionCode : "01");
        return callJson(mainApiUrl + "/api/verify/rims/license", body);
    }

    // ─── 화물운송자격증 ─────────────────────────────────────
    public JsonNode verifyCargo(String name, String birth, String lcnsNo) {
        return callJson(mainApiUrl + "/api/verify/cargo", Map.of(
                "name", safe(name),
                "birth", safe(birth),
                "lcnsNo", safe(lcnsNo)
        ));
    }

    // ─── KOSHA 안전보건교육 (multipart 이미지) ────────────
    // main-api KoshaVerifyController 의 @RequestParam("image") 와 매칭. "file" 로 보내면 400.
    public JsonNode verifyKosha(byte[] fileBytes, String filename) {
        return callMultipart(mainApiUrl + "/api/verify/kosha", fileBytes, filename, "image");
    }

    // ─── 범용 OCR 추출 (verify-api 직접) ──────────────────
    /**
     * type 매핑 (skep 호환):
     *   DRIVER_LICENSE / LICENSE      → LICENSE
     *   BUSINESS_REGISTRATION         → BUSINESS
     *   CARGO                          → CARGO
     *   EQUIPMENT_REGISTRATION         → EQUIPMENT_REGISTRATION
     *   KOSHA                          → KOSHA
     */
    public JsonNode extractOcr(String type, byte[] fileBytes, String filename) {
        String t = type == null ? "" : type.toUpperCase();
        String mapped = switch (t) {
            case "DRIVER_LICENSE", "LICENSE" -> "LICENSE";
            case "BUSINESS_REGISTRATION", "BUSINESS" -> "BUSINESS";
            case "CARGO", "CARGO_LICENSE" -> "CARGO";
            case "VEHICLE_LICENSE", "EQUIPMENT_REGISTRATION" -> "EQUIPMENT_REGISTRATION";
            case "KOSHA", "KOSHA_CERTIFICATE", "SAFETY_TRAINING" -> "KOSHA";
            default -> "LICENSE";
        };
        return callMultipart(innerApiUrl + "/verify/ocr/extract/" + mapped, fileBytes, filename, "image");
    }

    // ─── 공통 호출 헬퍼 ────────────────────────────────────

    private JsonNode callJson(String url, Map<String, ?> body) {
        if (!enabled) return upstreamDisabled();
        try {
            String response = webClient.post()
                    .uri(url)
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            return objectMapper.readTree(response != null ? response : "{}");
        } catch (Exception e) {
            log.warn("verify call failed url={} error={}", url, e.getMessage());
            return upstreamError(e.getMessage());
        }
    }

    private JsonNode callMultipart(String url, byte[] fileBytes, String filename, String fieldName) {
        if (!enabled) return upstreamDisabled();
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part(fieldName, new ByteArrayResource(fileBytes) {
                @Override public String getFilename() { return filename; }
            }).contentType(MediaType.APPLICATION_OCTET_STREAM);

            String response = webClient.post()
                    .uri(url)
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();
            return objectMapper.readTree(response != null ? response : "{}");
        } catch (Exception e) {
            log.warn("verify multipart call failed url={} error={}", url, e.getMessage());
            return upstreamError(e.getMessage());
        }
    }

    private JsonNode upstreamError(String message) {
        try {
            return objectMapper.readTree(String.format(
                    "{\"verified\":false,\"result\":\"UNKNOWN\",\"reasonCode\":\"UPSTREAM_ERROR\",\"message\":%s}",
                    objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode upstreamDisabled() {
        try {
            return objectMapper.readTree(
                    "{\"verified\":false,\"result\":\"UNKNOWN\",\"reasonCode\":\"UPSTREAM_DISABLED\","
                            + "\"message\":\"verify.enabled=false — 외부 verify-api 호출이 비활성화 상태입니다.\"}");
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public boolean isEnabled() { return enabled; }
}
