package com.example.verifyapi.service.impl;

import com.example.verifyapi.dto.kosha.OCRData;
import com.example.verifyapi.service.OCRService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * Stub OCR 서비스 (API 키 미설정 시 / 테스트용)
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * - Google Vision API 키가 없을 때 자동 사용
 * - 실제 OCR 수행하지 않음 (isAvailable = false)
 * - KOSHA 조회만으로 1차 검증 가능
 */
public class StubOCRService implements OCRService {

    private static final Logger log = LoggerFactory.getLogger(StubOCRService.class);

    public StubOCRService() {
        log.warn("========================================");
        log.warn("StubOCRService 활성화 - OCR 비활성(API 키 없음/테스트용)");
        log.warn("실제 OCR을 수행하지 않으며, KOSHA 조회만으로 검증합니다.");
        log.warn("운영 환경에서는 GOOGLE_VISION_API_KEY를 설정하세요.");
        log.warn("========================================");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public OCRData extractText(BufferedImage image, String requestId) {
        log.info("[{}] StubOCRService - OCR 비활성(API 키 없음/테스트용), OCR 건너뜀", requestId);

        OCRData data = new OCRData();
        data.setFullText("[StubOCR] OCR 비활성(API 키 없음/테스트용) - 실제 OCR을 수행하지 않았습니다.");

        return data;
    }
}
