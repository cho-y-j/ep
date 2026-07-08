package com.example.verifyapi.dto.kosha;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * QR + OCR 추출 결과 (검증 없이 추출만 수행한 결과)
 * - 디버깅 / 미리보기 용도
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractResult {

    private String requestId;
    private boolean success;
    private String message;

    private QRCodeData qrData;
    private OCRData ocrData;

    public ExtractResult() {}

    public static ExtractResult success(String requestId, QRCodeData qrData, OCRData ocrData) {
        ExtractResult result = new ExtractResult();
        result.setRequestId(requestId);
        result.setSuccess(true);
        result.setMessage("추출 성공");
        result.setQrData(qrData);
        result.setOcrData(ocrData);
        return result;
    }

    public static ExtractResult failure(String requestId, String message) {
        ExtractResult result = new ExtractResult();
        result.setRequestId(requestId);
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
}
