package com.example.verifyapi.service;

import com.example.verifyapi.dto.kosha.QRCodeData;
import com.example.verifyapi.exception.VerifyException;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * QR 코드 추출 서비스 (ZXing 기반)
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * - 이미지 여러 영역에서 QR 코드 추출 시도
 * - QR 결과가 URL이면 q, ptSignature 쿼리 파라미터 파싱
 * - ptSignature 없고 q만 있으면 HTML에서 hidden input 파싱
 */
@Service
public class QRCodeService {

    private static final Logger log = LoggerFactory.getLogger(QRCodeService.class);

    private final Map<DecodeHintType, Object> hints;

    public QRCodeService() {
        this.hints = new EnumMap<>(DecodeHintType.class);
        this.hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        this.hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
    }

    /**
     * 이미지에서 QR 코드를 추출하고 KOSHA 검증에 필요한 데이터 파싱
     */
    public QRCodeData extractQRCode(BufferedImage image, String requestId) {
        log.debug("[{}] QR 코드 추출 시작, 이미지 크기: {}x{}", requestId, image.getWidth(), image.getHeight());

        String qrContent = tryExtractQR(image, requestId);

        if (qrContent == null) {
            log.warn("[{}] QR 코드를 찾을 수 없음", requestId);
            throw new VerifyException("QR_NOT_FOUND", "이미지에서 QR 코드를 찾을 수 없습니다");
        }

        log.debug("[{}] QR 코드 내용: {}", requestId, qrContent);

        return parseQRContent(qrContent, requestId);
    }

    /**
     * 이미지 여러 영역에서 QR 코드 추출 시도
     */
    private String tryExtractQR(BufferedImage image, String requestId) {
        // 1. 전체 이미지에서 시도
        String result = decodeQR(image);
        if (result != null) {
            log.debug("[{}] 전체 이미지에서 QR 발견", requestId);
            return result;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 2. 하단 50% 영역
        result = decodeRegion(image, 0, height / 2, width, height / 2);
        if (result != null) {
            log.debug("[{}] 하단 영역에서 QR 발견", requestId);
            return result;
        }

        // 3. 우하단 50% 영역
        result = decodeRegion(image, width / 2, height / 2, width / 2, height / 2);
        if (result != null) {
            log.debug("[{}] 우하단 영역에서 QR 발견", requestId);
            return result;
        }

        // 4. 좌하단 50% 영역
        result = decodeRegion(image, 0, height / 2, width / 2, height / 2);
        if (result != null) {
            log.debug("[{}] 좌하단 영역에서 QR 발견", requestId);
            return result;
        }

        // 5. 중앙 영역 (30% 마진)
        int marginX = (int) (width * 0.3);
        int marginY = (int) (height * 0.3);
        result = decodeRegion(image, marginX, marginY, width - marginX * 2, height - marginY * 2);
        if (result != null) {
            log.debug("[{}] 중앙 영역에서 QR 발견", requestId);
            return result;
        }

        // 6. 우측 40% 영역
        result = decodeRegion(image, (int) (width * 0.6), 0, (int) (width * 0.4), height);
        if (result != null) {
            log.debug("[{}] 우측 영역에서 QR 발견", requestId);
            return result;
        }

        return null;
    }

    /**
     * 특정 영역에서 QR 코드 디코딩
     */
    private String decodeRegion(BufferedImage image, int x, int y, int regionWidth, int regionHeight) {
        try {
            if (x < 0 || y < 0 || regionWidth <= 0 || regionHeight <= 0) {
                return null;
            }
            if (x + regionWidth > image.getWidth() || y + regionHeight > image.getHeight()) {
                return null;
            }

            BufferedImage subImage = image.getSubimage(x, y, regionWidth, regionHeight);
            return decodeQR(subImage);
        } catch (Exception e) {
            log.trace("영역 QR 디코딩 실패: x={}, y={}, w={}, h={}", x, y, regionWidth, regionHeight);
            return null;
        }
    }

    /**
     * ZXing을 사용한 QR 코드 디코딩
     */
    private String decodeQR(BufferedImage image) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            log.trace("QR 디코딩 예외: {}", e.getMessage());
            return null;
        }
    }

    /**
     * QR 코드 내용 파싱 (URL에서 q, ptSignature 추출)
     */
    private QRCodeData parseQRContent(String content, String requestId) {
        QRCodeData data = new QRCodeData();
        data.setUrl(content);

        try {
            // URL 형식인지 확인
            if (!content.startsWith("http://") && !content.startsWith("https://")) {
                log.warn("[{}] QR 코드가 URL 형식이 아님: {}", requestId, content);
                throw new VerifyException("QR_INVALID_FORMAT", "QR 코드가 올바른 URL 형식이 아닙니다");
            }

            URI uri = new URI(content);
            String query = uri.getRawQuery();

            if (query != null) {
                Map<String, String> params = parseQueryParams(query);
                data.setQ(params.get("q"));
                data.setPtSignature(params.get("ptSignature"));
            }

            // ptSignature가 없고 q만 있는 경우: HTML에서 파싱 시도
            if (data.getQ() != null && !data.getQ().isBlank() &&
                (data.getPtSignature() == null || data.getPtSignature().isBlank())) {
                log.debug("[{}] ptSignature 없음, HTML에서 추출 시도", requestId);
                fetchSignatureFromHtml(data, content, requestId);
            }

            log.info("[{}] QR 파싱 완료: q={}, ptSignature={}",
                     requestId,
                     data.getQ() != null ? "[PRESENT]" : "null",
                     data.getPtSignature() != null ? "[PRESENT]" : "null");

            return data;

        } catch (VerifyException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] QR 파싱 오류: {}", requestId, e.getMessage());
            throw new VerifyException("QR_PARSE_ERROR", "QR 코드 파싱 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * 쿼리 스트링 파싱
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    /**
     * HTML 페이지에서 hidden input으로 q, ptSignature 추출
     */
    private void fetchSignatureFromHtml(QRCodeData data, String url, String requestId) {
        try {
            log.debug("[{}] HTML 페이지 접근: {}", requestId, url);

            Document doc = Jsoup.connect(url)
                    .timeout(5000)
                    .userAgent("Mozilla/5.0")
                    .get();

            // hidden input에서 q, ptSignature 추출
            Element qInput = doc.selectFirst("input[name=q]");
            Element ptSignatureInput = doc.selectFirst("input[name=ptSignature]");

            if (qInput != null && data.getQ() == null) {
                data.setQ(qInput.val());
            }

            if (ptSignatureInput != null) {
                data.setPtSignature(ptSignatureInput.val());
                log.debug("[{}] HTML에서 ptSignature 추출 성공", requestId);
            } else {
                log.warn("[{}] HTML에서 ptSignature를 찾을 수 없음", requestId);
            }

        } catch (Exception e) {
            log.warn("[{}] HTML 파싱 실패: {}", requestId, e.getMessage());
            // HTML 파싱 실패는 치명적 오류가 아님 - 진행 계속
        }
    }
}
