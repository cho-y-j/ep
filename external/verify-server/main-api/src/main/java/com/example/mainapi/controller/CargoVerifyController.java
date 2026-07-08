package com.example.mainapi.controller;

import com.example.mainapi.dto.CargoVerifyRequest;
import com.example.mainapi.dto.CargoVerifyResponse;
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
@RequestMapping("/api/verify")
@Tag(name = "자격증 검증", description = "자격증 진위여부 검증 API")
public class CargoVerifyController {

    private final RestTemplate restTemplate;

    @Value("${verify.api.url:http://verify-api:8081}")
    private String verifyApiUrl;

    @Value("${VERIFY_API_KEY:}")
    private String verifyApiKey;

    public CargoVerifyController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/cargo")
    @Operation(summary = "화물운송 자격증 검증", description = "화물운송(운수종사자) 자격증의 진위여부를 검증합니다")
    public ResponseEntity<CargoVerifyResponse> verifyCargo(
            @Valid @RequestBody CargoVerifyRequest request) {

        String requestId = UUID.randomUUID().toString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", verifyApiKey);

            HttpEntity<CargoVerifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CargoVerifyResponse> response = restTemplate.postForEntity(
                    verifyApiUrl + "/verify/cargo",
                    entity,
                    CargoVerifyResponse.class
            );

            CargoVerifyResponse body = response.getBody();
            if (body != null) {
                body.setRequestId(requestId);
            }
            return ResponseEntity.ok(body);

        } catch (RestClientException e) {
            CargoVerifyResponse errorResponse = new CargoVerifyResponse();
            errorResponse.setRequestId(requestId);
            errorResponse.setResult("UNKNOWN");
            errorResponse.setReasonCode("UPSTREAM_ERROR");
            errorResponse.setProvider("PUBLIC_API");
            errorResponse.setVerifiedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(errorResponse);
        }
    }
}
