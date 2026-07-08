package com.example.verifyapi.service;

import com.example.verifyapi.dto.kosha.OCRData;

import java.awt.image.BufferedImage;

/**
 * OCR 서비스 인터페이스
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * - 구현체: GoogleVisionOCRService (운영), StubOCRService (개발/테스트)
 * - API 키가 없으면 Stub 구현체 자동 사용
 */
public interface OCRService {

    /**
     * 이미지에서 텍스트 추출
     *
     * @param image 분석할 이미지
     * @param requestId 요청 ID (로깅용)
     * @return OCR 추출 결과
     */
    OCRData extractText(BufferedImage image, String requestId);

    /**
     * OCR 서비스 사용 가능 여부
     */
    boolean isAvailable();
}
