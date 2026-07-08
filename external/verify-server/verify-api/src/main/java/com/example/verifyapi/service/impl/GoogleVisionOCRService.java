package com.example.verifyapi.service.impl;

import com.example.verifyapi.dto.kosha.OCRData;
import com.example.verifyapi.exception.VerifyException;
import com.example.verifyapi.service.OCRService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Cloud Vision OCR 구현체 (REST API 방식)
 */
public class GoogleVisionOCRService implements OCRService {

    private static final Logger log = LoggerFactory.getLogger(GoogleVisionOCRService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String endpoint;

    private static final Pattern NAME_PATTERN = Pattern.compile("(?:성명|이름|성 명)[:\\s]*([가-힣]{2,4}|[A-Za-z\\s]+)");
    private static final Pattern BIRTH_PATTERN = Pattern.compile("(?:생년월일|생년|생일)[:\\s]*(\\d{4})[.\\-/]?(\\d{2})[.\\-/]?(\\d{2})");
    private static final Pattern REG_NUM_PATTERN = Pattern.compile("(?:등록번호|이수번호|증서번호|번호)[:\\s]*([A-Z0-9\\-]+)");

    public GoogleVisionOCRService(RestTemplate restTemplate, String apiKey, String endpoint) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        log.info("GoogleVisionOCRService 초기화 완료");
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public OCRData extractText(BufferedImage image, String requestId) {
        log.debug("[{}] Google Vision OCR 시작", requestId);

        try {
            String base64Image = encodeImageToBase64(image);
            String requestBody = buildRequestBody(base64Image);
            String responseBody = callVisionApi(requestBody, requestId);

            // 응답 파싱 - fullText + textAnnotations
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode responses = root.path("responses");

            if (responses.isEmpty() || !responses.isArray() || responses.size() == 0) {
                log.warn("[{}] Vision API 응답에 결과 없음", requestId);
                OCRData data = new OCRData();
                data.setFullText("");
                return data;
            }

            JsonNode firstResponse = responses.get(0);

            // 에러 체크
            JsonNode error = firstResponse.path("error");
            if (!error.isMissingNode()) {
                String errorMessage = error.path("message").asText("Unknown error");
                log.error("[{}] Vision API 에러: {}", requestId, errorMessage);
                throw new VerifyException("OCR_API_ERROR", "Vision API 에러: " + errorMessage);
            }

            // textAnnotations 파싱 (전체 텍스트 + 개별 요소 + boundingPoly)
            String fullText = "";
            List<OCRData.TextAnnotation> annotations = new ArrayList<>();

            JsonNode textAnnotations = firstResponse.path("textAnnotations");
            if (!textAnnotations.isEmpty() && textAnnotations.isArray() && textAnnotations.size() > 0) {
                // 첫 번째 = 전체 텍스트
                fullText = textAnnotations.get(0).path("description").asText("");

                // 나머지 = 개별 단어/토큰 + boundingPoly
                for (int i = 1; i < textAnnotations.size(); i++) {
                    JsonNode annotation = textAnnotations.get(i);
                    String desc = annotation.path("description").asText("");
                    JsonNode boundingPoly = annotation.path("boundingPoly");
                    JsonNode vertices = boundingPoly.path("vertices");

                    if (!vertices.isEmpty() && vertices.isArray() && vertices.size() == 4) {
                        int[][] coords = new int[4][2];
                        for (int j = 0; j < 4; j++) {
                            coords[j][0] = vertices.get(j).path("x").asInt(0);
                            coords[j][1] = vertices.get(j).path("y").asInt(0);
                        }
                        annotations.add(new OCRData.TextAnnotation(desc, coords));
                    }
                }
                log.debug("[{}] textAnnotations 파싱: 전체텍스트 {}자, 개별요소 {}개",
                        requestId, fullText.length(), annotations.size());
            }

            // fallback: fullTextAnnotation
            if (fullText.isBlank()) {
                JsonNode fullTextAnnotation = firstResponse.path("fullTextAnnotation");
                if (!fullTextAnnotation.isMissingNode()) {
                    fullText = fullTextAnnotation.path("text").asText("");
                }
            }

            log.debug("[{}] OCR 전체 텍스트 길이: {}", requestId, fullText.length());

            OCRData data = parseOCRText(fullText, requestId);
            data.setFullText(fullText);
            data.setTextAnnotations(annotations);

            return data;

        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Google Vision OCR 오류: {}", requestId, e.getMessage(), e);
            throw new VerifyException("OCR_FAILED", "OCR 처리 중 오류 발생: " + e.getMessage(), e);
        }
    }

    private String encodeImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String buildRequestBody(String base64Image) throws Exception {
        return objectMapper.writeValueAsString(new VisionRequest(base64Image));
    }

    private String callVisionApi(String requestBody, String requestId) {
        String url = endpoint + "?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.debug("[{}] Vision API 호출", requestId);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("[{}] Vision API 오류 응답: {}", requestId, response.getStatusCode());
                throw new VerifyException("OCR_API_ERROR", "Vision API 오류: " + response.getStatusCode());
            }

            return response.getBody();

        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Vision API 호출 실패: {}", requestId, e.getMessage());
            throw new VerifyException("OCR_API_ERROR", "Vision API 호출 실패: " + e.getMessage(), e);
        }
    }

    private OCRData parseOCRText(String fullText, String requestId) {
        OCRData data = new OCRData();

        Matcher nameMatcher = NAME_PATTERN.matcher(fullText);
        if (nameMatcher.find()) {
            data.setName(nameMatcher.group(1).trim());
            log.debug("[{}] 이름 추출: {}", requestId, data.getName());
        }

        Matcher birthMatcher = BIRTH_PATTERN.matcher(fullText);
        if (birthMatcher.find()) {
            String birthDate = birthMatcher.group(1) + birthMatcher.group(2) + birthMatcher.group(3);
            data.setBirthDate(birthDate);
            log.debug("[{}] 생년월일 추출: {}", requestId, data.getBirthDate());
        }

        Matcher regMatcher = REG_NUM_PATTERN.matcher(fullText);
        if (regMatcher.find()) {
            data.setRegistrationNumber(regMatcher.group(1).trim());
            log.debug("[{}] 등록번호 추출: {}", requestId, data.getRegistrationNumber());
        }

        log.info("[{}] OCR 파싱 결과: {}", requestId, data);
        return data;
    }

    private static class VisionRequest {
        public VisionRequestItem[] requests;

        public VisionRequest(String base64Image) {
            this.requests = new VisionRequestItem[]{new VisionRequestItem(base64Image)};
        }
    }

    private static class VisionRequestItem {
        public VisionImage image;
        public VisionFeature[] features;

        public VisionRequestItem(String base64Image) {
            this.image = new VisionImage(base64Image);
            this.features = new VisionFeature[]{new VisionFeature()};
        }
    }

    private static class VisionImage {
        public String content;

        public VisionImage(String content) {
            this.content = content;
        }
    }

    private static class VisionFeature {
        public String type = "TEXT_DETECTION";
        public int maxResults = 1;
    }
}
