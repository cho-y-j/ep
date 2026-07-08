package com.example.verifyapi.service;

import com.example.verifyapi.client.KoshaClient;
import com.example.verifyapi.dto.kosha.*;
import com.example.verifyapi.exception.VerifyException;
import com.example.verifyapi.util.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KOSHA 교육이수증 검증 서비스
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * 검증 흐름:
 * 1. QR 코드 추출 → q, ptSignature 확보
 * 2. OCR 텍스트 추출 (API 키 있을 때만)
 * 3. KOSHA 포털 조회 → 원본 데이터 획득 (1차 인증)
 * 4. OCR vs KOSHA 데이터 비교 (보조 검증)
 * 5. 최종 판정
 */
@Service
public class KoshaVerificationService {

    private static final Logger log = LoggerFactory.getLogger(KoshaVerificationService.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final QRCodeService qrCodeService;
    private final OCRService ocrService;
    private final KoshaClient koshaClient;
    private final boolean strictMode;

    public KoshaVerificationService(
            QRCodeService qrCodeService,
            OCRService ocrService,
            KoshaClient koshaClient,
            @Value("${verify.strict-mode:false}") boolean strictMode) {
        this.qrCodeService = qrCodeService;
        this.ocrService = ocrService;
        this.koshaClient = koshaClient;
        this.strictMode = strictMode;
        log.info("KoshaVerificationService 초기화 완료: strictMode={}", strictMode);
    }

    /**
     * 이미지에서 교육이수증 검증 수행
     */
    public VerificationResult verify(BufferedImage image, String requestId) {
        log.info("[{}] 검증 시작", requestId);

        QRCodeData qrData = null;
        OCRData ocrData = null;
        KoshaOriginalData originalData = null;

        try {
            // 1. QR 코드 추출
            qrData = qrCodeService.extractQRCode(image, requestId);
            log.debug("[{}] QR 추출 완료: {}", requestId, qrData);

            // 2. OCR 처리
            ocrData = processOCR(image, requestId);

            // 3. KOSHA 포털 조회
            originalData = koshaClient.fetchTrainingInfo(qrData, requestId);
            log.debug("[{}] KOSHA 조회 완료: {}", requestId, originalData);

            // 4. 검증 판정
            return buildVerificationResult(requestId, qrData, ocrData, originalData);

        } catch (VerifyException e) {
            // QR 코드 없으면 OCR fallback
            if ("QR_NOT_FOUND".equals(e.getReasonCode())) {
                log.info("[{}] QR 코드 없음, OCR fallback 진행", requestId);
                return verifyByOcrFallback(image, requestId);
            }
            log.warn("[{}] 검증 실패: {} - {}", requestId, e.getReasonCode(), e.getMessage());
            return buildErrorResult(requestId, e, qrData, ocrData, originalData);
        } catch (Exception e) {
            log.error("[{}] 검증 중 예기치 않은 오류: {}", requestId, e.getMessage(), e);
            VerificationResult result = VerificationResult.unknown(requestId, "INTERNAL_ERROR", "내부 오류: " + e.getMessage());
            result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
            result.setStrictMode(strictMode);
            return result;
        }
    }

    /**
     * QR 코드 없을 때 OCR fallback 검증
     * OCR로 이름/생년월일 추출 성공 시 VALID 처리
     */
    private VerificationResult verifyByOcrFallback(BufferedImage image, String requestId) {
        try {
            OCRData ocrData = processOCR(image, requestId);

            if (ocrData.getName() != null && !ocrData.getName().isBlank()) {
                log.info("[{}] OCR fallback 성공: name={}, birthDate={}", requestId, ocrData.getName(), ocrData.getBirthDate());
                VerificationResult result = VerificationResult.success(requestId);
                result.setReasonCode("OCR_VERIFIED");
                result.setMessage("교육이수증 확인 완료 (OCR 검증)");
                result.setOcrData(ocrData);
                result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
                result.setStrictMode(strictMode);
                return result;
            }

            log.warn("[{}] OCR fallback 실패: 이름 추출 불가", requestId);
            VerificationResult result = VerificationResult.unknown(requestId, "OCR_FAILED", "QR 코드 없고 OCR에서 이름을 추출할 수 없습니다");
            result.setOcrData(ocrData);
            result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
            result.setStrictMode(strictMode);
            return result;

        } catch (Exception e) {
            log.error("[{}] OCR fallback 중 오류: {}", requestId, e.getMessage());
            VerificationResult result = VerificationResult.unknown(requestId, "OCR_ERROR", "OCR 처리 중 오류: " + e.getMessage());
            result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
            result.setStrictMode(strictMode);
            return result;
        }
    }

    /**
     * QR + OCR 추출만 수행 (검증 없음)
     */
    public ExtractResult extract(BufferedImage image, String requestId) {
        log.info("[{}] 추출 시작 (검증 없음)", requestId);

        try {
            QRCodeData qrData = qrCodeService.extractQRCode(image, requestId);
            OCRData ocrData = processOCR(image, requestId);

            return ExtractResult.success(requestId, qrData, ocrData);

        } catch (VerifyException e) {
            log.warn("[{}] 추출 실패: {} - {}", requestId, e.getReasonCode(), e.getMessage());
            return ExtractResult.failure(requestId, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] 추출 중 오류: {}", requestId, e.getMessage());
            return ExtractResult.failure(requestId, "추출 실패: " + e.getMessage());
        }
    }

    /**
     * OCR 처리 (strict-mode에 따른 분기)
     */
    private OCRData processOCR(BufferedImage image, String requestId) {
        if (!ocrService.isAvailable()) {
            if (strictMode) {
                // strict-mode=true + OCR 비활성 → UNKNOWN으로 종료
                log.warn("[{}] strict-mode에서 OCR 비활성 상태", requestId);
                throw new VerifyException("OCR_DISABLED", "strict-mode에서 OCR이 비활성화되어 검증할 수 없습니다");
            }
            // strict-mode=false + OCR 비활성 → OCR SKIP
            log.info("[{}] OCR 비활성, KOSHA 조회만으로 검증 진행", requestId);
            return new OCRData();
        }

        return ocrService.extractText(image, requestId);
    }

    /**
     * 검증 결과 생성
     */
    private VerificationResult buildVerificationResult(
            String requestId,
            QRCodeData qrData,
            OCRData ocrData,
            KoshaOriginalData originalData) {

        // KOSHA 조회 성공이 1차 인증
        // OCR 비교는 보조 수단

        Map<String, Boolean> matchDetails = compareData(ocrData, originalData, requestId);
        boolean isValid = determineValidity(matchDetails, requestId);

        VerificationResult result;
        if (isValid) {
            result = VerificationResult.success(requestId);
        } else {
            result = VerificationResult.invalid(requestId, "MISMATCH", "OCR 데이터와 KOSHA 원본 데이터 불일치");
        }

        result.setQrData(qrData);
        result.setOcrData(ocrData);
        result.setOriginalData(originalData);
        result.setMatchDetails(matchDetails);
        result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
        result.setStrictMode(strictMode);

        log.info("[{}] 검증 완료: result={}, matchDetails={}", requestId, result.getResult(), matchDetails);
        return result;
    }

    /**
     * OCR 데이터와 KOSHA 원본 데이터 비교
     */
    private Map<String, Boolean> compareData(OCRData ocrData, KoshaOriginalData originalData, String requestId) {
        Map<String, Boolean> matchDetails = new LinkedHashMap<>();

        // 이름 비교
        String ocrName = TextNormalizer.normalizeName(ocrData.getName());
        String originalName = TextNormalizer.normalizeName(originalData.getName());
        Boolean nameMatch = TextNormalizer.compareNormalized(ocrName, originalName);
        if (nameMatch != null) {
            matchDetails.put("name", nameMatch);
            log.debug("[{}] 이름 비교: OCR={}, KOSHA={}, match={}", requestId, ocrName, originalName, nameMatch);
        }

        // 생년월일 비교
        String ocrBirth = TextNormalizer.normalizeBirthDate(ocrData.getBirthDate());
        String originalBirth = TextNormalizer.normalizeBirthDate(originalData.getBirthDate());
        Boolean birthMatch = TextNormalizer.compareNormalized(ocrBirth, originalBirth);
        if (birthMatch != null) {
            matchDetails.put("birthDate", birthMatch);
            log.debug("[{}] 생년월일 비교: OCR={}, KOSHA={}, match={}", requestId, ocrBirth, originalBirth, birthMatch);
        }

        // 등록번호 비교
        String ocrRegNum = TextNormalizer.normalizeRegistrationNumber(ocrData.getRegistrationNumber());
        String originalRegNum = TextNormalizer.normalizeRegistrationNumber(originalData.getRegistrationNumber());
        Boolean regNumMatch = TextNormalizer.compareNormalized(ocrRegNum, originalRegNum);
        if (regNumMatch != null) {
            matchDetails.put("registrationNumber", regNumMatch);
            log.debug("[{}] 등록번호 비교: OCR={}, KOSHA={}, match={}", requestId, ocrRegNum, originalRegNum, regNumMatch);
        }

        return matchDetails;
    }

    /**
     * 유효성 판정
     * - strict-mode=false: KOSHA 조회 성공 + 비교 가능한 필드 1개 이상 + 존재 필드 모두 일치 → VALID
     * - strict-mode=true: KOSHA 조회 성공 + 모든 비교 대상 필드 존재 + 모든 필드 일치 → VALID
     * - OCR 비활성 시 matchDetails가 비어있으면 KOSHA 조회 성공만으로 VALID (strict-mode=false일 때)
     */
    private boolean determineValidity(Map<String, Boolean> matchDetails, String requestId) {
        // OCR이 비활성이면 matchDetails가 비어있음
        if (matchDetails.isEmpty()) {
            if (strictMode) {
                log.warn("[{}] strict-mode에서 비교 가능한 필드 없음 → INVALID", requestId);
                return false;
            }
            // strict-mode=false: KOSHA 조회 성공만으로 VALID
            log.info("[{}] OCR 비활성, KOSHA 조회 성공 → VALID", requestId);
            return true;
        }

        // 모든 비교 필드가 true인지 확인
        boolean allMatch = matchDetails.values().stream().allMatch(Boolean::booleanValue);

        if (strictMode) {
            // strict-mode: 최소 3개 필드 필요 (name, birthDate, registrationNumber)
            if (matchDetails.size() < 3) {
                log.warn("[{}] strict-mode에서 비교 필드 부족: {} < 3", requestId, matchDetails.size());
                return false;
            }
            return allMatch;
        }

        // 일반 모드: 비교 가능한 필드가 1개 이상이고 모두 일치
        return !matchDetails.isEmpty() && allMatch;
    }

    /**
     * 에러 결과 생성
     */
    private VerificationResult buildErrorResult(
            String requestId,
            VerifyException e,
            QRCodeData qrData,
            OCRData ocrData,
            KoshaOriginalData originalData) {

        String resultType = determineResultType(e.getReasonCode());
        VerificationResult result;

        if ("INVALID".equals(resultType)) {
            result = VerificationResult.invalid(requestId, e.getReasonCode(), e.getMessage());
        } else {
            result = VerificationResult.unknown(requestId, e.getReasonCode(), e.getMessage());
        }

        result.setQrData(qrData);
        result.setOcrData(ocrData);
        result.setOriginalData(originalData);
        result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));
        result.setStrictMode(strictMode);

        return result;
    }

    /**
     * 에러 코드에 따른 결과 유형 결정
     */
    private String determineResultType(String reasonCode) {
        // KOSHA 조회 실패 → INVALID (위조 가능성)
        // 기타 오류 → UNKNOWN (판단 불가)
        return switch (reasonCode) {
            case "KOSHA_LOOKUP_FAILED", "KOSHA_PARSE_ERROR", "QR_INCOMPLETE" -> "INVALID";
            default -> "UNKNOWN";
        };
    }
}
