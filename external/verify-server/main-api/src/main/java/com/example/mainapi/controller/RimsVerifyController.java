package com.example.mainapi.controller;

import com.example.mainapi.dto.rims.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * RIMS 운전면허 검증 API 컨트롤러 (main-api)
 * - verify-api 내부 호출을 프록시
 * - Swagger UI로 외부 노출
 */
@RestController
@RequestMapping("/api/verify/rims")
@Tag(name = "운전면허 검증", description = "RIMS 운전면허 진위여부 검증 API")
public class RimsVerifyController {

    private final RestTemplate restTemplate;

    @Value("${verify.api.url:http://verify-api:8081}")
    private String verifyApiUrl;

    @Value("${verify.api.key:}")
    private String verifyApiKey;

    public RimsVerifyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostMapping("/license")
    @Operation(
            summary = "운전면허 단건 검증",
            description = "RIMS(한국교통안전공단)를 통해 운전면허의 진위여부를 검증합니다"
    )
    public ResponseEntity<RimsLicenseVerifyResponse> verifyLicense(
            @Valid @RequestBody RimsLicenseVerifyRequest request) {

        String requestId = UUID.randomUUID().toString();

        // verifyApiKey 체크
        if (verifyApiKey == null || verifyApiKey.isBlank()) {
            return ResponseEntity.ok(createSingleErrorResponse(requestId, "VERIFY_API_KEY_MISSING"));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", verifyApiKey);

            HttpEntity<RimsLicenseVerifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<RimsLicenseVerifyResponse> response = restTemplate.postForEntity(
                    verifyApiUrl + "/verify/rims/license",
                    entity,
                    RimsLicenseVerifyResponse.class
            );

            RimsLicenseVerifyResponse body = response.getBody();
            if (body == null) {
                return ResponseEntity.ok(createSingleErrorResponse(requestId, "UPSTREAM_EMPTY_BODY"));
            }
            body.setRequestId(requestId);
            return ResponseEntity.ok(body);

        } catch (RestClientException e) {
            return ResponseEntity.ok(createSingleErrorResponse(requestId, "UPSTREAM_ERROR"));
        }
    }

    @PostMapping("/license/batch")
    @Operation(
            summary = "운전면허 배치 검증",
            description = "RIMS(한국교통안전공단)를 통해 여러 건의 운전면허 진위여부를 일괄 검증합니다 (최대 1000건)"
    )
    public ResponseEntity<RimsLicenseBatchVerifyResponse> verifyLicenseBatch(
            @Valid @RequestBody RimsLicenseBatchVerifyRequest request) {

        String requestId = UUID.randomUUID().toString();
        int requestSize = request.getRequestList() != null ? request.getRequestList().size() : 0;

        // verifyApiKey 체크
        if (verifyApiKey == null || verifyApiKey.isBlank()) {
            return ResponseEntity.ok(createBatchErrorResponse(requestId, "VERIFY_API_KEY_MISSING", requestSize));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", verifyApiKey);

            HttpEntity<RimsLicenseBatchVerifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<RimsLicenseBatchVerifyResponse> response = restTemplate.postForEntity(
                    verifyApiUrl + "/verify/rims/license/batch",
                    entity,
                    RimsLicenseBatchVerifyResponse.class
            );

            RimsLicenseBatchVerifyResponse body = response.getBody();
            if (body == null) {
                return ResponseEntity.ok(createBatchErrorResponse(requestId, "UPSTREAM_EMPTY_BODY", requestSize));
            }
            body.setRequestId(requestId);
            return ResponseEntity.ok(body);

        } catch (RestClientException e) {
            return ResponseEntity.ok(createBatchErrorResponse(requestId, "UPSTREAM_ERROR", requestSize));
        }
    }

    private RimsLicenseVerifyResponse createSingleErrorResponse(String requestId, String reasonCode) {
        RimsLicenseVerifyResponse errorResponse = new RimsLicenseVerifyResponse();
        errorResponse.setRequestId(requestId);
        errorResponse.setResult("UNKNOWN");
        errorResponse.setReasonCode(reasonCode);
        errorResponse.setProvider("RIMS");
        errorResponse.setVerifiedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return errorResponse;
    }

    private RimsLicenseBatchVerifyResponse createBatchErrorResponse(String requestId, String reasonCode, int totalCount) {
        RimsLicenseBatchVerifyResponse errorResponse = new RimsLicenseBatchVerifyResponse();
        errorResponse.setRequestId(requestId);
        errorResponse.setProvider("RIMS");
        errorResponse.setVerifiedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        errorResponse.setTotalCount(totalCount);
        errorResponse.setValidCount(0);
        errorResponse.setInvalidCount(0);
        errorResponse.setUnknownCount(totalCount);
        return errorResponse;
    }
}
