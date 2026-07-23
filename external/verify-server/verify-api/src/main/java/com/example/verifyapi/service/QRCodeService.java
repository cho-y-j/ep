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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
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

    /** 업스케일 폴백 시 결과 이미지 한 변 최대 크기 (메모리 보호 — 큰 사진은 업스케일 불필요) */
    private static final int MAX_UPSCALE_SIDE = 4096;
    /** 이진화 고정 임계값 (562x820 저해상 실측: 128 성공, Otsu 자동값 164 실패) */
    private static final int BINARIZE_THRESHOLD = 128;

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

        // 7. 전처리 폴백 체인 (저해상/저품질 업로드 대응)
        result = tryPreprocessedQR(image, requestId);
        if (result != null) {
            return result;
        }

        return null;
    }

    /**
     * 전처리 폴백 체인: 원본/영역 시도에서 QR을 못 찾은 경우
     * (a) 2배/3배 바이큐빅 업스케일 → (b) 그레이스케일+이진화(±2배 업스케일) 순으로 재시도
     *
     * 562x820 저해상 저장본 실측: 원본 미검출 → 업스케일 x2 즉시 검출.
     * 더 열화된 사본(x0.7)은 x3에서만 검출되어 x3까지 시도한다.
     */
    private String tryPreprocessedQR(BufferedImage image, String requestId) {
        int maxSide = Math.max(image.getWidth(), image.getHeight());

        // (a) 업스케일 — 저해상 이미지의 QR 모듈 해상도 확보
        for (double factor : new double[]{2.0, 3.0}) {
            if (maxSide * factor > MAX_UPSCALE_SIDE) {
                continue;
            }
            String result = decodeQR(upscale(image, factor));
            if (result != null) {
                log.debug("[{}] 업스케일 x{} 후 QR 발견", requestId, factor);
                return result;
            }
        }

        // (b) 그레이스케일+고정 임계 이진화 — 흐릿한 회색 모듈/저대비 대응
        BufferedImage binarized = binarize(image, BINARIZE_THRESHOLD);
        String result = decodeQR(binarized);
        if (result != null) {
            log.debug("[{}] 이진화 후 QR 발견", requestId);
            return result;
        }
        if (maxSide * 2.0 <= MAX_UPSCALE_SIDE) {
            result = decodeQR(upscale(binarized, 2.0));
            if (result != null) {
                log.debug("[{}] 이진화+업스케일 x2 후 QR 발견", requestId);
                return result;
            }
        }

        return null;
    }

    /**
     * 바이큐빅 보간 업스케일
     */
    private BufferedImage upscale(BufferedImage src, double factor) {
        int newWidth = (int) Math.round(src.getWidth() * factor);
        int newHeight = (int) Math.round(src.getHeight() * factor);
        BufferedImage out = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return out;
    }

    /**
     * 그레이스케일 변환 후 고정 임계값 이진화
     */
    private BufferedImage binarize(BufferedImage src, int threshold) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        WritableRaster raster = gray.getRaster();
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                raster.setSample(x, y, 0, raster.getSample(x, y, 0) < threshold ? 0 : 255);
            }
        }
        return gray;
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
