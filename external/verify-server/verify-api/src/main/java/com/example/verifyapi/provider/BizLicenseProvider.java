package com.example.verifyapi.provider;

import com.example.verifyapi.dto.biz.InternalBizRequest;
import com.example.verifyapi.dto.biz.InternalBizResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BizLicenseProvider {

    private static final Logger log = LoggerFactory.getLogger(BizLicenseProvider.class);
    private static final String VALIDATE_PATH = "/validate";

    private final RestTemplate restTemplate;

    @Value("${nts.api.base-url:https://api.odcloud.kr/api/nts-businessman/v1}")
    private String ntsApiBaseUrl;

    @Value("${nts.api.service-key:}")
    private String ntsApiServiceKey;

    public BizLicenseProvider(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public InternalBizResponse verify(InternalBizRequest request) {
        String verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (ntsApiServiceKey == null || ntsApiServiceKey.isBlank()) {
            log.warn("NTS API 서비스키가 설정되지 않음 - 시뮬레이션 모드 동작");
            return simulateVerification(request, verifiedAt);
        }

        // startDate와 ownerName이 없으면 상태조회 API 사용
        boolean useStatusApi = (request.getStartDate() == null || request.getStartDate().isBlank())
                && (request.getOwnerName() == null || request.getOwnerName().isBlank());

        if (useStatusApi) {
            return verifyByStatus(request, verifiedAt);
        }

        try {
            URI uri = buildUri();
            Map<String, Object> requestBody = buildRequestBody(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("NTS API 호출: {}", uri);
            @SuppressWarnings("unchecked")
            Map<String, Object> apiResponse = restTemplate.postForObject(uri, entity, Map.class);

            return parseApiResponse(apiResponse, verifiedAt);

        } catch (ResourceAccessException e) {
            log.error("NTS API 타임아웃: {}", e.getMessage());
            InternalBizResponse response = InternalBizResponse.unknown("TIMEOUT", "국세청 API 응답 시간 초과");
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (RestClientException e) {
            log.error("NTS API 호출 실패: {}", e.getMessage());
            InternalBizResponse response = InternalBizResponse.unknown("UPSTREAM_ERROR", "국세청 API 호출 실패");
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (Exception e) {
            log.error("NTS API 응답 파싱 실패: {}", e.getMessage(), e);
            InternalBizResponse response = InternalBizResponse.unknown("PARSE_ERROR", "응답 데이터 파싱 실패");
            response.setVerifiedAt(verifiedAt);
            return response;
        }
    }

    private URI buildUri() {
        return UriComponentsBuilder.fromHttpUrl(ntsApiBaseUrl)
                .path(VALIDATE_PATH)
                .queryParam("serviceKey", ntsApiServiceKey)
                .build()
                .encode()
                .toUri();
    }

    private Map<String, Object> buildRequestBody(InternalBizRequest request) {
        Map<String, Object> business = new HashMap<>();
        business.put("b_no", request.getBizNo());
        business.put("start_dt", request.getStartDate());
        business.put("p_nm", request.getOwnerName());

        if (request.getOwnerName2() != null && !request.getOwnerName2().isBlank()) {
            business.put("p_nm2", request.getOwnerName2());
        }
        if (request.getBizName() != null && !request.getBizName().isBlank()) {
            business.put("b_nm", request.getBizName());
        }
        if (request.getCorpNo() != null && !request.getCorpNo().isBlank()) {
            business.put("corp_no", request.getCorpNo());
        }
        if (request.getBizSector() != null && !request.getBizSector().isBlank()) {
            business.put("b_sector", request.getBizSector());
        }
        if (request.getBizType() != null && !request.getBizType().isBlank()) {
            business.put("b_type", request.getBizType());
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("businesses", List.of(business));

        return requestBody;
    }

    @SuppressWarnings("unchecked")
    private InternalBizResponse parseApiResponse(Map<String, Object> apiResponse, String verifiedAt) {
        if (apiResponse == null) {
            InternalBizResponse response = InternalBizResponse.unknown("EMPTY_RESPONSE", "빈 응답 수신");
            response.setVerifiedAt(verifiedAt);
            return response;
        }

        InternalBizResponse response;

        try {
            String statusCode = (String) apiResponse.get("status_code");

            if (!"OK".equals(statusCode)) {
                response = InternalBizResponse.unknown("API_ERROR_" + statusCode, "API 오류: " + statusCode);
                response.setVerifiedAt(verifiedAt);
                response.setRaw(apiResponse);
                return response;
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) apiResponse.get("data");

            if (dataList == null || dataList.isEmpty()) {
                response = InternalBizResponse.unknown("NO_DATA", "검증 데이터 없음");
                response.setVerifiedAt(verifiedAt);
                response.setRaw(apiResponse);
                return response;
            }

            Map<String, Object> data = dataList.get(0);
            String valid = (String) data.get("valid");
            String validMsg = (String) data.get("valid_msg");

            if ("01".equals(valid)) {
                response = InternalBizResponse.valid(validMsg != null ? validMsg : "확인됨");
            } else if ("02".equals(valid)) {
                response = InternalBizResponse.invalid(validMsg != null ? validMsg : "확인 불가");
            } else {
                response = InternalBizResponse.unknown("UNKNOWN_VALID_CODE_" + valid,
                        validMsg != null ? validMsg : "알 수 없는 검증 코드");
            }

            response.setVerifiedAt(verifiedAt);
            response.setRaw(apiResponse);
            return response;

        } catch (ClassCastException e) {
            log.error("API 응답 형식 오류: {}", e.getMessage());
            response = InternalBizResponse.unknown("PARSE_ERROR", "응답 형식 오류");
            response.setVerifiedAt(verifiedAt);
            response.setRaw(apiResponse);
            return response;
        }
    }

    /**
     * 사업자등록번호만으로 상태조회 (개업일자/대표자명 없을 때)
     */
    @SuppressWarnings("unchecked")
    private InternalBizResponse verifyByStatus(InternalBizRequest request, String verifiedAt) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(ntsApiBaseUrl)
                    .path("/status")
                    .queryParam("serviceKey", ntsApiServiceKey)
                    .build()
                    .encode()
                    .toUri();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("b_no", List.of(request.getBizNo()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("NTS Status API 호출: {}", uri);
            Map<String, Object> apiResponse = restTemplate.postForObject(uri, entity, Map.class);

            if (apiResponse == null) {
                InternalBizResponse response = InternalBizResponse.unknown("EMPTY_RESPONSE", "빈 응답 수신");
                response.setVerifiedAt(verifiedAt);
                return response;
            }

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) apiResponse.get("data");
            if (dataList == null || dataList.isEmpty()) {
                InternalBizResponse response = InternalBizResponse.unknown("NO_DATA", "검증 데이터 없음");
                response.setVerifiedAt(verifiedAt);
                response.setRaw(apiResponse);
                return response;
            }

            Map<String, Object> data = dataList.get(0);
            String bStt = (String) data.get("b_stt");       // 계속사업자, 휴업자, 폐업자
            String bSttCd = (String) data.get("b_stt_cd");   // 01: 계속, 02: 휴업, 03: 폐업
            String taxType = (String) data.get("tax_type");

            InternalBizResponse response;
            if ("01".equals(bSttCd)) {
                response = InternalBizResponse.valid("사업자 상태: " + (bStt != null ? bStt : "계속사업자") + " (" + (taxType != null ? taxType : "") + ")");
            } else if ("02".equals(bSttCd)) {
                response = InternalBizResponse.invalid("사업자 상태: 휴업자");
            } else if ("03".equals(bSttCd)) {
                response = InternalBizResponse.invalid("사업자 상태: 폐업자");
            } else {
                response = InternalBizResponse.unknown("STATUS_" + bSttCd, "사업자 상태: " + (bStt != null ? bStt : "확인 불가"));
            }

            response.setProvider("NTS_STATUS_API");
            response.setVerifiedAt(verifiedAt);
            response.setRaw(apiResponse);
            return response;

        } catch (ResourceAccessException e) {
            log.error("NTS Status API 타임아웃: {}", e.getMessage());
            InternalBizResponse response = InternalBizResponse.unknown("TIMEOUT", "국세청 API 응답 시간 초과");
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (RestClientException e) {
            log.error("NTS Status API 호출 실패: {}", e.getMessage());
            InternalBizResponse response = InternalBizResponse.unknown("UPSTREAM_ERROR", "국세청 API 호출 실패");
            response.setVerifiedAt(verifiedAt);
            return response;
        }
    }

    private InternalBizResponse simulateVerification(InternalBizRequest request, String verifiedAt) {
        InternalBizResponse response;

        if (request.getBizNo() != null && request.getBizNo().startsWith("000")) {
            response = InternalBizResponse.invalid("시뮬레이션: 등록되지 않은 사업자");
        } else if (request.getBizNo() != null && request.getBizNo().startsWith("999")) {
            response = InternalBizResponse.unknown("SIMULATION_ERROR", "시뮬레이션: API 오류");
        } else {
            response = InternalBizResponse.valid("시뮬레이션: 확인됨");
        }

        response.setVerifiedAt(verifiedAt);
        return response;
    }
}
