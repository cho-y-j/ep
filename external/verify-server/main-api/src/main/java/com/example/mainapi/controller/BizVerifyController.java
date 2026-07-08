package com.example.mainapi.controller;

import com.example.mainapi.dto.biz.BizVerifyRequest;
import com.example.mainapi.dto.biz.BizVerifyResponse;
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
@Tag(name = "사업자등록정보 검증", description = "국세청 사업자등록정보 진위확인 API")
public class BizVerifyController {

    private final RestTemplate restTemplate;

    @Value("${verify.api.url:http://verify-api:8081}")
    private String verifyApiUrl;

    @Value("${VERIFY_API_KEY:}")
    private String verifyApiKey;

    public BizVerifyController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping("/biz")
    @Operation(summary = "사업자등록정보 진위확인", description = "사업자번호, 개업일자, 대표자명을 기준으로 국세청 사업자등록정보의 진위여부를 검증합니다")
    public ResponseEntity<BizVerifyResponse> verifyBiz(
            @Valid @RequestBody BizVerifyRequest request) {

        String requestId = UUID.randomUUID().toString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", verifyApiKey);

            HttpEntity<BizVerifyRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BizVerifyResponse> response = restTemplate.postForEntity(
                    verifyApiUrl + "/verify/biz",
                    entity,
                    BizVerifyResponse.class
            );

            BizVerifyResponse body = response.getBody();
            if (body != null) {
                body.setRequestId(requestId);
            }
            return ResponseEntity.ok(body);

        } catch (RestClientException e) {
            BizVerifyResponse errorResponse = new BizVerifyResponse();
            errorResponse.setRequestId(requestId);
            errorResponse.setResult("UNKNOWN");
            errorResponse.setReasonCode("UPSTREAM_ERROR");
            errorResponse.setMessage("내부 검증 서버 연결 실패");
            errorResponse.setProvider("NTS_API");
            errorResponse.setVerifiedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(errorResponse);
        }
    }
}
