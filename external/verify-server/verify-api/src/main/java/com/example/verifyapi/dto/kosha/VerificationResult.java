package com.example.verifyapi.dto.kosha;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 교육이수증 검증 최종 결과
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationResult {

    private String requestId;
    private String result;           // VALID, INVALID, UNKNOWN
    private String reasonCode;       // SUCCESS, QR_NOT_FOUND, OCR_FAILED, KOSHA_LOOKUP_FAILED, etc.
    private String message;
    private String verifiedAt;

    private QRCodeData qrData;
    private OCRData ocrData;
    private KoshaOriginalData originalData;
    private Map<String, Boolean> matchDetails;

    private boolean strictMode;

    public VerificationResult() {}

    // === Static factory methods ===

    public static VerificationResult success(String requestId) {
        VerificationResult result = new VerificationResult();
        result.setRequestId(requestId);
        result.setResult("VALID");
        result.setReasonCode("SUCCESS");
        result.setMessage("교육이수증 검증 성공");
        return result;
    }

    public static VerificationResult invalid(String requestId, String reasonCode, String message) {
        VerificationResult result = new VerificationResult();
        result.setRequestId(requestId);
        result.setResult("INVALID");
        result.setReasonCode(reasonCode);
        result.setMessage(message);
        return result;
    }

    public static VerificationResult unknown(String requestId, String reasonCode, String message) {
        VerificationResult result = new VerificationResult();
        result.setRequestId(requestId);
        result.setResult("UNKNOWN");
        result.setReasonCode(reasonCode);
        result.setMessage(message);
        return result;
    }

    // === Getters and Setters ===

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(String verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public QRCodeData getQrData() {
        return qrData;
    }

    public void setQrData(QRCodeData qrData) {
        this.qrData = qrData;
    }

    public OCRData getOcrData() {
        return ocrData;
    }

    public void setOcrData(OCRData ocrData) {
        this.ocrData = ocrData;
    }

    public KoshaOriginalData getOriginalData() {
        return originalData;
    }

    public void setOriginalData(KoshaOriginalData originalData) {
        this.originalData = originalData;
    }

    public Map<String, Boolean> getMatchDetails() {
        return matchDetails;
    }

    public void setMatchDetails(Map<String, Boolean> matchDetails) {
        this.matchDetails = matchDetails;
    }

    public boolean isStrictMode() {
        return strictMode;
    }

    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }
}
