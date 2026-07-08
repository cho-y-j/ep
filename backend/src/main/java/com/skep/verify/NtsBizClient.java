package com.skep.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * S-9-G: 공공데이터포털 사업자등록상태조회 API 직접 호출 클라이언트.
 *
 * verify-api / main-api 외부 의존성 없이 NTS 검증 가능.
 *
 * 발급: https://www.data.go.kr/data/15081808/openapi.do (무료, 일일 호출 제한 있음)
 * 응답:
 * {
 *   "status_code": "OK",
 *   "match_cnt": 1,
 *   "data": [{
 *     "b_no": "1234567890",
 *     "b_stt": "계속사업자" | "휴업자" | "폐업자",
 *     "b_stt_cd": "01" | "02" | "03",
 *     "tax_type": "...",
 *     "end_dt": "20231231"
 *   }]
 * }
 *
 * b_stt_cd 매핑:
 *   01 계속사업자 → verified=true
 *   02 휴업자    → verified=false (reasonCode=NTS_SUSPENDED)
 *   03 폐업자    → verified=false (reasonCode=NTS_CLOSED)
 *   기타          → verified=false (reasonCode=NTS_INVALID)
 */
@Component
public class NtsBizClient {

    private static final Logger log = LoggerFactory.getLogger(NtsBizClient.class);
    private static final String STATUS_URL = "https://api.odcloud.kr/api/nts-businessman/v1/status";

    @Value("${nts.service-key:${NTS_SERVICE_KEY:}}")
    private String serviceKey;

    private WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void init() {
        this.webClient = WebClient.builder().build();
        log.info("NtsBizClient init: serviceKey={}",
                (serviceKey == null || serviceKey.isBlank()) ? "(empty)" : "(set)");
    }

    public boolean isEnabled() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /** 사업자번호 1개 상태 조회. 정상화된 b_no(하이픈 제거 10자리) 사용. */
    public JsonNode lookupStatus(String bizNo) {
        String b_no = normalize(bizNo);
        if (b_no.length() != 10) {
            return upstreamError("BIZNO_MALFORMED", "사업자번호가 10자리가 아닙니다: " + bizNo);
        }
        if (!isEnabled()) {
            return upstreamDisabled();
        }
        try {
            String url = STATUS_URL + "?serviceKey=" + serviceKey;
            Map<String, Object> body = Map.of("b_no", List.of(b_no));
            String response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            JsonNode root = mapper.readTree(response != null ? response : "{}");
            return interpret(root);
        } catch (Exception e) {
            log.warn("NTS lookup failed b_no={} error={}", b_no, e.getMessage());
            return upstreamError("UPSTREAM_ERROR", e.getMessage());
        }
    }

    /** 응답을 표준화: verified, result, reasonCode, message + raw 보존. */
    private JsonNode interpret(JsonNode raw) {
        try {
            String statusCode = raw.has("status_code") ? raw.get("status_code").asText() : "";
            JsonNode data = raw.has("data") ? raw.get("data") : null;
            if (!"OK".equals(statusCode) || data == null || !data.isArray() || data.isEmpty()) {
                return mapper.readTree(String.format(
                        "{\"verified\":false,\"result\":\"UNKNOWN\",\"reasonCode\":\"NTS_NO_DATA\",\"raw\":%s}",
                        raw.toString()));
            }
            JsonNode first = data.get(0);
            String bSttCd = first.has("b_stt_cd") ? first.get("b_stt_cd").asText() : "";
            String bStt = first.has("b_stt") ? first.get("b_stt").asText() : "";

            boolean verified = "01".equals(bSttCd);
            String reasonCode = switch (bSttCd) {
                case "01" -> "NTS_ACTIVE";
                case "02" -> "NTS_SUSPENDED";
                case "03" -> "NTS_CLOSED";
                default -> "NTS_INVALID";
            };
            String result = verified ? "VERIFIED" : "REJECTED";

            return mapper.readTree(String.format(
                    "{\"verified\":%b,\"result\":\"%s\",\"reasonCode\":\"%s\",\"message\":\"NTS 응답: %s\",\"raw\":%s}",
                    verified, result, reasonCode, escape(bStt), raw.toString()));
        } catch (Exception e) {
            log.warn("NTS interpret failed: {}", e.getMessage());
            return upstreamError("INTERPRET_ERROR", e.getMessage());
        }
    }

    private static String normalize(String bizNo) {
        if (bizNo == null) return "";
        return bizNo.replaceAll("[^0-9]", "");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private JsonNode upstreamError(String code, String message) {
        try {
            return mapper.readTree(String.format(
                    "{\"verified\":false,\"result\":\"UNKNOWN\",\"reasonCode\":\"%s\",\"message\":%s}",
                    code, mapper.writeValueAsString(message == null ? "" : message)));
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private JsonNode upstreamDisabled() {
        try {
            return mapper.readTree(
                    "{\"verified\":false,\"result\":\"UNKNOWN\",\"reasonCode\":\"NTS_DISABLED\","
                            + "\"message\":\"NTS_SERVICE_KEY 가 설정되지 않아 자동 검증이 비활성화 상태입니다.\"}");
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
