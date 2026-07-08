package com.example.verifyapi.provider;

import com.example.verifyapi.dto.InternalCargoRequest;
import com.example.verifyapi.dto.InternalCargoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CargoLicenseProvider {

    private static final Logger log = LoggerFactory.getLogger(CargoLicenseProvider.class);

    private static final String LCNS_CHECK_PATH = "/lcnsCheck";
    private static final String CARGO_TYPE = "2";

    private final RestTemplate restTemplate;

    @Value("${PUBLIC_API_BASE_URL:}")
    private String publicApiBaseUrl;

    @Value("${PUBLIC_API_SERVICE_KEY:}")
    private String publicApiServiceKey;

    public CargoLicenseProvider(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    public InternalCargoResponse verify(InternalCargoRequest request) {
        String verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        log.info("Cargo verify request: name={}, birth={}, lcnsNo={}, area={}",
                request.getName(), request.getBirth(), request.getLcnsNo(), request.getArea());

        if (publicApiBaseUrl == null || publicApiBaseUrl.isBlank() ||
            publicApiServiceKey == null || publicApiServiceKey.isBlank()) {
            log.warn("Public API config missing, using simulation");
            return simulateVerification(request, verifiedAt);
        }

        try {
            URI uri = buildUri(request);
            Map<?, ?> apiResponse = restTemplate.getForObject(uri, Map.class);
            log.info("Cargo API response: {}", apiResponse);
            return parseApiResponse(apiResponse, verifiedAt);

        } catch (ResourceAccessException e) {
            InternalCargoResponse response = InternalCargoResponse.unknown("TIMEOUT");
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (RestClientException e) {
            InternalCargoResponse response = InternalCargoResponse.unknown("UPSTREAM_ERROR");
            response.setVerifiedAt(verifiedAt);
            return response;
        } catch (Exception e) {
            InternalCargoResponse response = InternalCargoResponse.unknown("PARSE_ERROR");
            response.setVerifiedAt(verifiedAt);
            return response;
        }
    }

    private URI buildUri(InternalCargoRequest request) {
        String cleanLcnsNo = request.getLcnsNo() != null
                ? request.getLcnsNo().replace("-", "")
                : "";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(publicApiBaseUrl)
                .path(LCNS_CHECK_PATH)
                .queryParam("serviceKey", publicApiServiceKey)
                .queryParam("returnType", "json")
                .queryParam("name", request.getName())
                .queryParam("birth", convertBirthFormat(request.getBirth()))
                .queryParam("lcnsNo", cleanLcnsNo)
                .queryParam("type", CARGO_TYPE);

        if (request.getArea() != null && !request.getArea().isBlank()) {
            builder.queryParam("area", request.getArea());
        }

        URI uri = builder.build().encode().toUri();
        log.info("Cargo API URI: {}", uri.toString().replaceAll("serviceKey=[^&]+", "serviceKey=***"));
        return uri;
    }

    private static final Pattern YYMMDD_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern YYYYMMDD_PATTERN = Pattern.compile("^\\d{8}$");
    private static final DateTimeFormatter YYYY_MM_DD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YYYYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYMMDD_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    String convertBirthFormat(String birth) {
        if (birth == null || birth.isBlank()) {
            return null;
        }

        String trimmed = birth.trim();

        // 1. YYYY-MM-DD 형식 (LocalDate 파싱)
        try {
            LocalDate date = LocalDate.parse(trimmed, YYYY_MM_DD_FORMATTER);
            return date.format(YYMMDD_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }

        // 2. YYYYMMDD 형식 (8자리 숫자)
        if (YYYYMMDD_PATTERN.matcher(trimmed).matches()) {
            try {
                LocalDate date = LocalDate.parse(trimmed, YYYYMMDD_FORMATTER);
                return date.format(YYMMDD_FORMATTER);
            } catch (DateTimeParseException ignored) {
            }
            // fallback: 정규식 기반 앞 2자리 제거
            return trimmed.substring(2);
        }

        // 3. YYMMDD 형식 (6자리 숫자) - 그대로 반환
        if (YYMMDD_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        // 4. 그 외 포맷 - 안전하게 null 반환
        return null;
    }

    private InternalCargoResponse parseApiResponse(Map<?, ?> apiResponse, String verifiedAt) {
        if (apiResponse == null) {
            InternalCargoResponse response = InternalCargoResponse.unknown("EMPTY_RESPONSE");
            response.setVerifiedAt(verifiedAt);
            return response;
        }

        InternalCargoResponse response;

        String resultCode = extractHeaderResultCode(apiResponse);
        if (!"00".equals(resultCode)) {
            response = InternalCargoResponse.unknown("API_ERROR_" + resultCode);
            response.setVerifiedAt(verifiedAt);
            response.setRaw(apiResponse);
            return response;
        }

        String status = extractStatus(apiResponse);
        if ("O".equals(status)) {
            response = InternalCargoResponse.success();
        } else if ("X".equals(status)) {
            response = InternalCargoResponse.invalid();
        } else {
            Integer totalCount = extractTotalCount(apiResponse);
            if (totalCount != null && totalCount == 0) {
                response = InternalCargoResponse.unknown("NO_DATA");
            } else if (totalCount != null && totalCount > 0) {
                response = InternalCargoResponse.unknown("UNEXPECTED_SHAPE");
            } else {
                response = InternalCargoResponse.unknown("PARSE_ERROR");
            }
        }

        response.setVerifiedAt(verifiedAt);
        response.setRaw(apiResponse);
        return response;
    }

    @SuppressWarnings("unchecked")
    private String extractHeaderResultCode(Map<?, ?> apiResponse) {
        try {
            Object responseObj = apiResponse.get("response");
            if (!(responseObj instanceof Map)) {
                return null;
            }

            Map<?, ?> responseMap = (Map<?, ?>) responseObj;
            Object headerObj = responseMap.get("header");
            if (!(headerObj instanceof Map)) {
                return null;
            }

            Map<?, ?> headerMap = (Map<?, ?>) headerObj;
            Object resultCode = headerMap.get("resultCode");
            return resultCode != null ? resultCode.toString() : null;

        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractHeaderResultMsg(Map<?, ?> apiResponse) {
        try {
            Object responseObj = apiResponse.get("response");
            if (!(responseObj instanceof Map)) {
                return null;
            }

            Map<?, ?> responseMap = (Map<?, ?>) responseObj;
            Object headerObj = responseMap.get("header");
            if (!(headerObj instanceof Map)) {
                return null;
            }

            Map<?, ?> headerMap = (Map<?, ?>) headerObj;
            Object resultMsg = headerMap.get("resultMsg");
            return resultMsg != null ? resultMsg.toString() : null;

        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Integer extractTotalCount(Map<?, ?> apiResponse) {
        try {
            Object responseObj = apiResponse.get("response");
            if (!(responseObj instanceof Map)) {
                return null;
            }

            Map<?, ?> responseMap = (Map<?, ?>) responseObj;
            Object bodyObj = responseMap.get("body");
            if (!(bodyObj instanceof Map)) {
                return null;
            }

            Map<?, ?> bodyMap = (Map<?, ?>) bodyObj;
            Object itemsObj = bodyMap.get("items");
            if (!(itemsObj instanceof Map)) {
                return null;
            }

            Map<?, ?> itemsMap = (Map<?, ?>) itemsObj;
            Object totalCount = itemsMap.get("totalCount");
            if (totalCount instanceof Number) {
                return ((Number) totalCount).intValue();
            } else if (totalCount != null) {
                return Integer.parseInt(totalCount.toString());
            }
            return null;

        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String extractStatus(Map<?, ?> apiResponse) {
        try {
            Object responseObj = apiResponse.get("response");
            if (!(responseObj instanceof Map)) {
                return null;
            }

            Map<?, ?> responseMap = (Map<?, ?>) responseObj;
            Object bodyObj = responseMap.get("body");
            if (!(bodyObj instanceof Map)) {
                return null;
            }

            Map<?, ?> bodyMap = (Map<?, ?>) bodyObj;
            Object itemsObj = bodyMap.get("items");
            if (!(itemsObj instanceof Map)) {
                return null;
            }

            Map<?, ?> itemsMap = (Map<?, ?>) itemsObj;
            Object itemObj = itemsMap.get("item");

            Map<?, ?> itemMap = null;
            if (itemObj instanceof java.util.List) {
                java.util.List<?> itemList = (java.util.List<?>) itemObj;
                if (!itemList.isEmpty() && itemList.get(0) instanceof Map) {
                    itemMap = (Map<?, ?>) itemList.get(0);
                }
            } else if (itemObj instanceof Map) {
                itemMap = (Map<?, ?>) itemObj;
            }

            if (itemMap == null) {
                return null;
            }

            Object status = itemMap.get("status");
            return status != null ? status.toString() : null;

        } catch (Exception e) {
            return null;
        }
    }

    private InternalCargoResponse simulateVerification(InternalCargoRequest request, String verifiedAt) {
        InternalCargoResponse response;

        if (request.getLcnsNo() != null && request.getLcnsNo().startsWith("INVALID")) {
            response = InternalCargoResponse.invalid();
        } else if (request.getLcnsNo() != null && request.getLcnsNo().startsWith("ERROR")) {
            response = InternalCargoResponse.unknown("UPSTREAM_ERROR");
        } else {
            response = InternalCargoResponse.success();
        }

        response.setVerifiedAt(verifiedAt);
        return response;
    }
}
