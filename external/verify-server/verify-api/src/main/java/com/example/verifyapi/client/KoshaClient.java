package com.example.verifyapi.client;

import com.example.verifyapi.dto.kosha.KoshaOriginalData;
import com.example.verifyapi.dto.kosha.QRCodeData;
import com.example.verifyapi.exception.VerifyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * KOSHA 포털 조회 클라이언트
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * - POST https://portal-edu.kosha.or.kr/api/portal24/bizG/p/GSECH03001/selectTrneInfo
 * - body: { q, ptSignature }
 * - 응답 payload.trneInfo 파싱
 */
@Component
public class KoshaClient {

    private static final Logger log = LoggerFactory.getLogger(KoshaClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String endpoint;

    public KoshaClient(
            @Qualifier("koshaRestTemplate") RestTemplate restTemplate,
            @Value("${kosha.base-url}") String baseUrl,
            @Value("${kosha.endpoint}") String endpoint) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        this.endpoint = endpoint;
        log.info("KoshaClient 초기화 완료: {}{}", baseUrl, endpoint);
    }

    /**
     * KOSHA 포털에서 교육이수 정보 조회
     *
     * @param qrData QR 코드에서 추출한 q, ptSignature
     * @param requestId 요청 ID (로깅용)
     * @return KOSHA 원본 데이터 (조회 실패 시 null)
     */
    public KoshaOriginalData fetchTrainingInfo(QRCodeData qrData, String requestId) {
        log.debug("[{}] KOSHA 포털 조회 시작", requestId);

        if (!qrData.isReadyForVerification()) {
            log.warn("[{}] QR 데이터 불완전: q 또는 ptSignature 누락", requestId);
            throw new VerifyException("QR_INCOMPLETE", "QR 데이터가 불완전합니다 (q 또는 ptSignature 누락)");
        }

        try {
            // URL 조합 (슬래시 중복/누락 방지, 인코딩 이슈 예방)
            URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path(endpoint)
                    .build(true)
                    .toUri();

            // 요청 본문 생성
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("q", qrData.getQ());
            requestBody.put("ptSignature", qrData.getPtSignature());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
            headers.set("User-Agent", "Mozilla/5.0");
            headers.set("Origin", baseUrl);
            headers.set("Referer", baseUrl + "/");

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("[{}] KOSHA API 호출: {}", requestId, url);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("[{}] KOSHA API 오류 응답: status={}", requestId, response.getStatusCode());
                throw new VerifyException("KOSHA_LOOKUP_FAILED",
                        "KOSHA 포털 조회 실패: HTTP " + response.getStatusCode().value(), 502);
            }

            return parseResponse(response.getBody(), requestId);

        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] KOSHA API 호출 실패: {}", requestId, e.getMessage());
            throw new VerifyException("KOSHA_API_ERROR", "KOSHA 포털 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * KOSHA API 응답 파싱
     */
    private KoshaOriginalData parseResponse(String responseBody, String requestId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 응답 구조: { payload: { trneInfo: { ... } } } 또는 { data: { ... } }
            JsonNode payload = root.path("payload");
            JsonNode trneInfo = payload.path("trneInfo");

            // 대체 구조 확인
            if (trneInfo.isMissingNode() || trneInfo.isNull()) {
                trneInfo = root.path("data");
            }
            if (trneInfo.isMissingNode() || trneInfo.isNull()) {
                trneInfo = payload;
            }

            if (trneInfo.isMissingNode() || trneInfo.isNull() || trneInfo.isEmpty()) {
                log.warn("[{}] KOSHA 응답에 trneInfo 없음", requestId);
                log.debug("[{}] KOSHA 응답 본문(앞 300자): {}", requestId, truncate(responseBody, 300));
                throw new VerifyException("KOSHA_PARSE_ERROR", "KOSHA 응답에서 교육이수 정보를 찾을 수 없습니다", 502);
            }

            KoshaOriginalData data = new KoshaOriginalData();

            // 필드 매핑 (KOSHA 응답 필드명은 실제 API에 따라 조정 필요)
            data.setName(getStringValue(trneInfo, "nm", "name", "userName", "trneName"));
            data.setBirthDate(getStringValue(trneInfo, "brdt", "birthDate", "birth", "brthYmd"));
            data.setRegistrationNumber(getStringValue(trneInfo, "certNo", "registrationNumber", "regNo", "trneNo"));
            data.setPhoneNumber(getStringValue(trneInfo, "telNo", "phoneNumber", "phone", "mblTelno"));
            data.setPhotoFileNo(getStringValue(trneInfo, "photoFileNo", "fileNo"));
            data.setFileSeq(getStringValue(trneInfo, "fileSeq", "seq"));
            data.setEduName(getStringValue(trneInfo, "eduNm", "eduName", "courseName", "trneNm"));
            data.setEduDate(getStringValue(trneInfo, "eduDt", "eduDate", "completionDate", "trneDt"));

            log.info("[{}] KOSHA 조회 성공: {}", requestId, data);
            return data;

        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] KOSHA 응답 파싱 오류: {}", requestId, e.getMessage());
            log.debug("[{}] KOSHA 응답 본문(앞 300자): {}", requestId, truncate(responseBody, 300));
            throw new VerifyException("KOSHA_PARSE_ERROR", "KOSHA 응답 파싱 실패: " + e.getMessage(), 502);
        }
    }

    /**
     * 문자열을 지정된 길이로 자르기
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "[null]";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...(truncated)";
    }

    /**
     * JSON 노드에서 여러 필드명 중 하나로 값 추출
     */
    private String getStringValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (!field.isMissingNode() && !field.isNull()) {
                String value = field.asText();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return null;
    }
}
