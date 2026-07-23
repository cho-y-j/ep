package com.example.verifyapi.service.impl;

import com.example.verifyapi.dto.kosha.OCRData;
import com.example.verifyapi.service.OCRService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로컬 PaddleOCR(paddle-ocr FastAPI, PP-OCRv5) 구현체
 *
 * POST {ocr.paddle.url}/ocr?max_side=0 — multipart 필드명 image →
 * 응답 {fullText, lines:[{text, score, box:[[x,y]x4]}]} 를 OCRData 로 매핑.
 * - fullText: 구글 비전과 같은 읽기순서로 재조립된 전체 텍스트 (paddle reading_order)
 * - lines → textAnnotations: 박스 단위 텍스트+좌표 (주민번호 마스킹용)
 * - max_side=0: paddle 측 다운스케일 비활성 → 박스 좌표가 전송 이미지 좌표계와 일치
 *
 * fail-open: 미연결/타임아웃/오류 시 예외를 던지지 않고 빈 OCRData 반환
 * → KOSHA 검증은 OCR 비교 없이(KOSHA 조회만으로) 계속 진행된다.
 */
public class PaddleOCRService implements OCRService {

    private static final Logger log = LoggerFactory.getLogger(PaddleOCRService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    // GoogleVisionOCRService 와 동일한 파싱 규칙 (KOSHA 비교 필드: name/birthDate/registrationNumber)
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:성명|이름|성 명)[:\\s]*([가-힣]{2,4}|[A-Za-z\\s]+)");
    private static final Pattern BIRTH_PATTERN = Pattern.compile("(?:생년월일|생년|생일)[:\\s]*(\\d{4})[.\\-/]?(\\d{2})[.\\-/]?(\\d{2})");
    private static final Pattern REG_NUM_PATTERN = Pattern.compile("(?:등록번호|이수번호|증서번호|번호)[:\\s]*([A-Z0-9\\-]+)");

    public PaddleOCRService(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.baseUrl = baseUrl;
        log.info("PaddleOCRService 초기화 완료: url={}", baseUrl);
    }

    @Override
    public boolean isAvailable() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public OCRData extractText(BufferedImage image, String requestId) {
        log.debug("[{}] Paddle OCR 시작", requestId);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] pngBytes = baos.toByteArray();

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", new ByteArrayResource(pngBytes) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/ocr?max_side=0", new HttpEntity<>(body, headers), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[{}] Paddle OCR 오류 응답: {} (fail-open, OCR 없이 진행)", requestId, response.getStatusCode());
                OCRData data = new OCRData();
                data.setFullText("");
                return data;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String fullText = root.path("fullText").asText("");

            // lines → textAnnotations (박스 단위, 마스킹용)
            List<OCRData.TextAnnotation> annotations = new ArrayList<>();
            JsonNode lines = root.path("lines");
            if (lines.isArray()) {
                for (JsonNode line : lines) {
                    String text = line.path("text").asText("");
                    JsonNode box = line.path("box");
                    if (!text.isEmpty() && box.isArray() && box.size() == 4) {
                        int[][] vertices = new int[4][2];
                        for (int j = 0; j < 4; j++) {
                            vertices[j][0] = (int) box.path(j).path(0).asDouble(0);
                            vertices[j][1] = (int) box.path(j).path(1).asDouble(0);
                        }
                        annotations.add(new OCRData.TextAnnotation(text, vertices));
                    }
                }
            }

            log.debug("[{}] Paddle OCR 완료: 전체텍스트 {}자, 박스 {}개", requestId, fullText.length(), annotations.size());

            OCRData data = parseOCRText(fullText, requestId);
            data.setFullText(fullText);
            data.setTextAnnotations(annotations);
            return data;

        } catch (Exception e) {
            // fail-open: OCR 실패가 검증 전체를 UNKNOWN 으로 만들지 않도록 예외를 삼킨다
            log.warn("[{}] Paddle OCR 실패 (fail-open, OCR 없이 진행): {}", requestId, e.getMessage());
            OCRData data = new OCRData();
            data.setFullText("");
            return data;
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

        log.info("[{}] Paddle OCR 파싱 결과: {}", requestId, data);
        return data;
    }
}
