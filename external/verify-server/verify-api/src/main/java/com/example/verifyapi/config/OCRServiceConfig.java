package com.example.verifyapi.config;

import com.example.verifyapi.service.OCRService;
import com.example.verifyapi.service.impl.GoogleVisionOCRService;
import com.example.verifyapi.service.impl.StubOCRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * OCR 서비스 설정
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * - google.vision.api-key가 존재하면 GoogleVisionOCRService 사용
 * - 그렇지 않으면 StubOCRService 사용
 * - OCRService 빈은 정확히 1개만 생성됨
 */
@Configuration
public class OCRServiceConfig {

    private static final Logger log = LoggerFactory.getLogger(OCRServiceConfig.class);

    @Bean
    public OCRService ocrService(
            @Qualifier("visionRestTemplate") RestTemplate visionRestTemplate,
            @Value("${google.vision.api-key:}") String apiKey,
            @Value("${google.vision.endpoint:https://vision.googleapis.com/v1/images:annotate}") String endpoint) {

        if (apiKey != null && !apiKey.isBlank()) {
            log.info("Google Vision API 키 감지 - GoogleVisionOCRService 사용");
            return new GoogleVisionOCRService(visionRestTemplate, apiKey, endpoint);
        } else {
            log.info("Google Vision API 키 미설정 - StubOCRService 사용");
            return new StubOCRService();
        }
    }
}
