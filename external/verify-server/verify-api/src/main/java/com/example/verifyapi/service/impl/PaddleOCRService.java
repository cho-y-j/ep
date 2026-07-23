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

    // GoogleVisionOCRService 의 파싱 규칙 + paddle 저해상 오인식 내성 (KOSHA 비교 필드: name/birthDate/registrationNumber)
    // - 이름 라벨: 이수증 카드 실서식 "이 름" 포함
    // - 생년월일 라벨: 저해상 실측 오인식 "병년월일" 등 첫 글자 변형 허용
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:성\\s?명|이\\s?름)[:\\s]*([가-힣]{2,4}|[A-Za-z\\s]+)");
    private static final Pattern BIRTH_PATTERN = Pattern.compile("(?:[생병][년넌]월일|생년|생일)[:\\s]*(\\d{4})[.\\-/]?(\\d{2})[.\\-/]?(\\d{2})");
    private static final Pattern REG_NUM_PATTERN = Pattern.compile("(?:등록번호|이수번호|증서번호|번호)[:\\s]*([A-Z0-9\\-]+)");

    // 라벨 유실(오인식) 대비 값 패턴 폴백 — 후보가 1개로 유일할 때만 채택 (모호하면 미추출, 오탐 금지)
    private static final Pattern DATE_VALUE_PATTERN = Pattern.compile("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
    // KOSHA 등록번호 서식: 연도-기관-일련 (예: 2023-171-01629). 끝 일련 4~6자리라 날짜(일 1~2자리)와 구분됨
    private static final Pattern REG_NUM_VALUE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{1,4}-\\d{4,6})\\b");
    /** 값 패턴 폴백에서 생년월일로 인정할 최소 경과 연수 (교육일자/발급일자와 구분) */
    private static final int MIN_BIRTH_YEARS_AGO = 14;

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
        } else {
            String fallback = findBirthDateByValuePattern(fullText);
            if (fallback != null) {
                data.setBirthDate(fallback);
                log.debug("[{}] 생년월일 추출 (값 패턴 폴백): {}", requestId, fallback);
            }
        }

        Matcher regMatcher = REG_NUM_PATTERN.matcher(fullText);
        if (regMatcher.find()) {
            data.setRegistrationNumber(regMatcher.group(1).trim());
            log.debug("[{}] 등록번호 추출: {}", requestId, data.getRegistrationNumber());
        } else {
            String fallback = findRegistrationNumberByValuePattern(fullText);
            if (fallback != null) {
                data.setRegistrationNumber(fallback);
                log.debug("[{}] 등록번호 추출 (값 패턴 폴백): {}", requestId, fallback);
            }
        }

        log.info("[{}] Paddle OCR 파싱 결과: {}", requestId, data);
        return data;
    }

    /**
     * 라벨 없이 날짜 값 패턴만으로 생년월일 후보 탐색.
     * 최소 {@value #MIN_BIRTH_YEARS_AGO}년 이전 연도만 인정해 교육일자/발급일자를 배제하고,
     * 유일 후보일 때만 반환한다 (모호하면 null — 오탐 금지).
     */
    private String findBirthDateByValuePattern(String fullText) {
        int maxBirthYear = java.time.LocalDate.now().getYear() - MIN_BIRTH_YEARS_AGO;
        String candidate = null;
        Matcher m = DATE_VALUE_PATTERN.matcher(fullText);
        while (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            if (year < 1900 || year > maxBirthYear || month < 1 || month > 12 || day < 1 || day > 31) {
                continue;
            }
            String normalized = String.format("%04d%02d%02d", year, month, day);
            if (candidate == null) {
                candidate = normalized;
            } else if (!candidate.equals(normalized)) {
                return null; // 후보 복수 → 모호 → 미추출
            }
        }
        return candidate;
    }

    /**
     * 라벨 없이 KOSHA 등록번호 값 패턴만으로 후보 탐색. 유일 후보일 때만 반환 (모호하면 null).
     */
    private String findRegistrationNumberByValuePattern(String fullText) {
        String candidate = null;
        Matcher m = REG_NUM_VALUE_PATTERN.matcher(fullText);
        while (m.find()) {
            String value = m.group(1);
            if (candidate == null) {
                candidate = value;
            } else if (!candidate.equals(value)) {
                return null; // 후보 복수 → 모호 → 미추출
            }
        }
        return candidate;
    }
}
