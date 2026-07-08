package com.example.mainapi.controller;

import com.example.mainapi.dto.carstat.CarInspectionRequest;
import com.example.mainapi.dto.carstat.CarPerformanceRequest;
import com.example.mainapi.dto.carstat.CarStatResponse;
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

@RestController
@RequestMapping("/api/statistics")
@Tag(name = "자동차 통계", description = "자동차 성능점검/검사정보 통계 조회 API")
public class CarStatController {

    private final RestTemplate restTemplate;

    @Value("${verify.api.url:http://verify-api:8081}")
    private String verifyApiUrl;

    @Value("${VERIFY_API_KEY:}")
    private String verifyApiKey;

    public CarStatController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/car-performance")
    @Operation(summary = "자동차 성능점검 통계 조회",
               description = "등록년도, 월, 차종, 지역, 모델년도를 기준으로 자동차 성능점검 통계를 조회합니다")
    public ResponseEntity<CarStatResponse> getCarPerformanceStats(
            @Valid @RequestBody CarPerformanceRequest request) {

        String requestId = UUID.randomUUID().toString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", verifyApiKey);

            HttpEntity<CarPerformanceRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CarStatResponse> response = restTemplate.postForEntity(
                    verifyApiUrl + "/statistics/car-performance",
                    entity,
                    CarStatResponse.class
            );

            CarStatResponse body = response.getBody();
            if (body != null) {
                body.setRequestId(requestId);
            }
            return ResponseEntity.ok(body);

        } catch (RestClientException e) {
            CarStatResponse errorResponse = new CarStatResponse();
            errorResponse.setRequestId(requestId);
            errorResponse.setResult("UNKNOWN");
            errorResponse.setReasonCode("UPSTREAM_ERROR");
            errorResponse.setMessage("내부 서버 통신 오류: " + e.getMessage());
            errorResponse.setProvider("TS_CAR_API");
            errorResponse.setVerifiedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(errorResponse);
        }
    }

    @PostMapping("/car-inspection")
    @Operation(summary = "자동차 검사정보 통계 조회",
               description = "시작일, 종료일 및 선택 조건(법정동, 용도, 차종, 세부차종)을 기준으로 자동차 검사정보 통계를 조회합니다")
    public ResponseEntity<CarStatResponse> getCarInspectionStats(
            @Valid @RequestBody CarInspectionRequest request) {

        String requestId = UUID.randomUUID().toString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", verifyApiKey);

            HttpEntity<CarInspectionRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CarStatResponse> response = restTemplate.postForEntity(
                    verifyApiUrl + "/statistics/car-inspection",
                    entity,
                    CarStatResponse.class
            );

            CarStatResponse body = response.getBody();
            if (body != null) {
                body.setRequestId(requestId);
            }
            return ResponseEntity.ok(body);

        } catch (RestClientException e) {
            CarStatResponse errorResponse = new CarStatResponse();
            errorResponse.setRequestId(requestId);
            errorResponse.setResult("UNKNOWN");
            errorResponse.setReasonCode("UPSTREAM_ERROR");
            errorResponse.setMessage("내부 서버 통신 오류: " + e.getMessage());
            errorResponse.setProvider("TS_CAR_API");
            errorResponse.setVerifiedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(errorResponse);
        }
    }
}
