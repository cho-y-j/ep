package com.example.mainapi.controller;

import com.example.mainapi.dto.kosha.KoshaVerifyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * KOSHA 교육이수증 검증 API (main-api → verify-api 게이트웨이)
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 */
@RestController
@RequestMapping("/api/verify")
@Tag(name = "KOSHA 교육이수증 검증", description = "KOSHA 교육이수증 QR 기반 진위여부 검증 API")
public class KoshaVerifyController {

    private static final Logger log = LoggerFactory.getLogger(KoshaVerifyController.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final RestTemplate restTemplate;

    @Value("${verify.api.url:http://verify-api:8081}")
    private String verifyApiUrl;

    @Value("${VERIFY_API_KEY:}")
    private String verifyApiKey;

    public KoshaVerifyController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping(value = "/kosha", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "KOSHA 교육이수증 검증",
        description = "교육이수증 이미지(QR 코드 포함)를 업로드하여 KOSHA 포털 조회를 통해 진위여부를 검증합니다.\n\n" +
                      "**지원 파일 형식:** jpg, jpeg, png, pdf (최대 10MB)\n\n" +
                      "**검증 흐름:**\n" +
                      "1. QR 코드 추출 → q, ptSignature 파싱\n" +
                      "2. OCR 텍스트 추출 (Google Vision)\n" +
                      "3. KOSHA 포털 조회 (1차 인증)\n" +
                      "4. OCR vs KOSHA 데이터 비교 (보조 검증)\n\n" +
                      "**[주의]** 이 API는 공식 KOSHA API가 아니라 QR 조회 웹 절차를 자동화한 것입니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "검증 완료 (결과는 result 필드 확인)",
                     content = @Content(schema = @Schema(implementation = KoshaVerifyResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 (파일 형식 오류 등)"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<KoshaVerifyResponse> verifyKosha(
            @Parameter(description = "교육이수증 이미지 파일 (QR 코드 포함)", required = true)
            @RequestParam("image") MultipartFile image) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] KOSHA 검증 요청: filename={}, size={}", requestId, image.getOriginalFilename(), image.getSize());

        try {
            // verify-api로 파일 전송
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-API-KEY", verifyApiKey);

            // MultipartFile을 ByteArrayResource로 변환
            ByteArrayResource fileResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", fileResource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = verifyApiUrl + "/verify/kosha/upload";
            log.debug("[{}] verify-api 호출: {}", requestId, url);

            ResponseEntity<KoshaVerifyResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    KoshaVerifyResponse.class
            );

            KoshaVerifyResponse result = response.getBody();
            if (result != null) {
                log.info("[{}] 검증 완료: result={}, reasonCode={}", requestId, result.getResult(), result.getReasonCode());
            }
            return ResponseEntity.ok(result);

        } catch (RestClientException e) {
            log.error("[{}] verify-api 호출 실패: {}", requestId, e.getMessage());
            return ResponseEntity.ok(buildErrorResponse(requestId, "UPSTREAM_ERROR", "검증 서버 연결 실패: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[{}] 검증 처리 오류: {}", requestId, e.getMessage(), e);
            return ResponseEntity.ok(buildErrorResponse(requestId, "INTERNAL_ERROR", "내부 오류: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/kosha/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "KOSHA 교육이수증 데이터 추출 (검증 없음)",
        description = "QR 코드 및 OCR 데이터만 추출합니다. KOSHA 포털 검증은 수행하지 않습니다.\n" +
                      "디버깅/미리보기 용도로 사용합니다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추출 완료"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    public ResponseEntity<KoshaVerifyResponse> extractKosha(
            @Parameter(description = "교육이수증 이미지 파일", required = true)
            @RequestParam("image") MultipartFile image) {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[{}] KOSHA 추출 요청: filename={}, size={}", requestId, image.getOriginalFilename(), image.getSize());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-API-KEY", verifyApiKey);

            ByteArrayResource fileResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", fileResource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            String url = verifyApiUrl + "/verify/kosha/extract";

            ResponseEntity<KoshaVerifyResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    KoshaVerifyResponse.class
            );

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            log.error("[{}] 추출 처리 오류: {}", requestId, e.getMessage());
            return ResponseEntity.ok(buildErrorResponse(requestId, "INTERNAL_ERROR", "추출 실패: " + e.getMessage()));
        }
    }

    /**
     * 에러 응답 생성
     */
    private KoshaVerifyResponse buildErrorResponse(String requestId, String reasonCode, String message) {
        KoshaVerifyResponse response = new KoshaVerifyResponse();
        response.setRequestId(requestId);
        response.setResult("UNKNOWN");
        response.setReasonCode(reasonCode);
        response.setMessage(message);
        response.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
        return response;
    }
}
