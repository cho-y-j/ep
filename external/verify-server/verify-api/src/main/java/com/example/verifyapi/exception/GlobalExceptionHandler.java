package com.example.verifyapi.exception;

import com.example.verifyapi.dto.kosha.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 전역 예외 처리기
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * 모든 예외를 일관된 VerificationResult 형태로 반환
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * VerifyException 처리 - 검증 과정에서 발생하는 예외
     */
    @ExceptionHandler(VerifyException.class)
    public ResponseEntity<VerificationResult> handleVerifyException(VerifyException e) {
        String requestId = getOrCreateRequestId();

        VerificationResult result;
        if (e.getHttpStatus() >= 500) {
            result = VerificationResult.unknown(requestId, e.getReasonCode(), e.getMessage());
        } else {
            result = VerificationResult.invalid(requestId, e.getReasonCode(), e.getMessage());
        }
        result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));

        log.debug("[{}] VerifyException 처리: reasonCode={}, httpStatus={}",
                requestId, e.getReasonCode(), e.getHttpStatus());

        return ResponseEntity.status(e.getHttpStatus()).body(result);
    }

    /**
     * @Valid 검증 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<VerificationResult> handleValidationException(MethodArgumentNotValidException e) {
        String requestId = getOrCreateRequestId();

        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("입력값 검증 실패");

        log.debug("[{}] Validation failed: {}", requestId, errorMessage);

        VerificationResult result = VerificationResult.invalid(requestId, "VALIDATION_ERROR", errorMessage);
        result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 파일 업로드 크기 초과 예외 처리
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<VerificationResult> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        String requestId = getOrCreateRequestId();

        log.warn("[{}] 파일 업로드 크기 초과: {}", requestId, e.getMessage());

        VerificationResult result = VerificationResult.invalid(requestId, "FILE_TOO_LARGE", "파일 크기가 제한을 초과합니다");
        result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    /**
     * 기타 예기치 않은 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<VerificationResult> handleGenericException(Exception e) {
        String requestId = getOrCreateRequestId();

        log.error("[{}] 예기치 않은 오류: {}", requestId, e.getMessage(), e);

        VerificationResult result = VerificationResult.unknown(requestId, "INTERNAL_ERROR", "내부 서버 오류가 발생했습니다");
        result.setVerifiedAt(LocalDateTime.now().format(DATETIME_FORMAT));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * requestId 조회 또는 생성
     */
    private String getOrCreateRequestId() {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("requestId", requestId);
        }
        return requestId;
    }
}
