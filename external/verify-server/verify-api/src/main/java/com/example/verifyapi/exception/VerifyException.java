package com.example.verifyapi.exception;

/**
 * 검증 과정에서 발생하는 예외
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 */
public class VerifyException extends RuntimeException {

    private final String reasonCode;
    private final int httpStatus;

    public VerifyException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
        this.httpStatus = 400;
    }

    public VerifyException(String reasonCode, String message, int httpStatus) {
        super(message);
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
    }

    public VerifyException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
        this.httpStatus = 500;
    }

    public VerifyException(String reasonCode, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
