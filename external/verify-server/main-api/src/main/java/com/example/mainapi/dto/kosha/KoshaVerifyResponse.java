package com.example.mainapi.dto.kosha;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * KOSHA 교육이수증 검증 응답
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "KOSHA 교육이수증 검증 결과")
public class KoshaVerifyResponse {

    @Schema(description = "요청 ID", example = "a1b2c3d4")
    private String requestId;

    @Schema(description = "검증 결과", example = "VALID", allowableValues = {"VALID", "INVALID", "UNKNOWN"})
    private String result;

    @Schema(description = "결과 코드", example = "SUCCESS")
    private String reasonCode;

    @Schema(description = "결과 메시지", example = "교육이수증 검증 성공")
    private String message;

    @Schema(description = "검증 시각", example = "2024-01-15T10:30:00")
    private String verifiedAt;

    @Schema(description = "QR 코드 데이터")
    private QRCodeData qrData;

    @Schema(description = "OCR 추출 데이터")
    private OCRData ocrData;

    @Schema(description = "KOSHA 원본 데이터")
    private KoshaOriginalData originalData;

    @Schema(description = "필드별 매칭 결과")
    private Map<String, Boolean> matchDetails;

    @Schema(description = "strict-mode 여부")
    private boolean strictMode;

    // Getters and Setters
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(String verifiedAt) { this.verifiedAt = verifiedAt; }

    public QRCodeData getQrData() { return qrData; }
    public void setQrData(QRCodeData qrData) { this.qrData = qrData; }

    public OCRData getOcrData() { return ocrData; }
    public void setOcrData(OCRData ocrData) { this.ocrData = ocrData; }

    public KoshaOriginalData getOriginalData() { return originalData; }
    public void setOriginalData(KoshaOriginalData originalData) { this.originalData = originalData; }

    public Map<String, Boolean> getMatchDetails() { return matchDetails; }
    public void setMatchDetails(Map<String, Boolean> matchDetails) { this.matchDetails = matchDetails; }

    public boolean isStrictMode() { return strictMode; }
    public void setStrictMode(boolean strictMode) { this.strictMode = strictMode; }

    /**
     * QR 코드 데이터
     */
    @Schema(description = "QR 코드에서 추출한 데이터")
    public static class QRCodeData {
        @Schema(description = "q 파라미터")
        private String q;
        @Schema(description = "ptSignature 파라미터")
        private String ptSignature;
        @Schema(description = "QR 원본 URL")
        private String url;

        public String getQ() { return q; }
        public void setQ(String q) { this.q = q; }
        public String getPtSignature() { return ptSignature; }
        public void setPtSignature(String ptSignature) { this.ptSignature = ptSignature; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    /**
     * OCR 추출 데이터
     */
    @Schema(description = "OCR로 추출한 데이터")
    public static class OCRData {
        @Schema(description = "이름")
        private String name;
        @Schema(description = "생년월일 (YYYYMMDD)")
        private String birthDate;
        @Schema(description = "등록번호")
        private String registrationNumber;
        @Schema(description = "OCR 전체 텍스트")
        private String fullText;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBirthDate() { return birthDate; }
        public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
        public String getRegistrationNumber() { return registrationNumber; }
        public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
        public String getFullText() { return fullText; }
        public void setFullText(String fullText) { this.fullText = fullText; }
    }

    /**
     * KOSHA 원본 데이터
     */
    @Schema(description = "KOSHA 포털 조회 결과")
    public static class KoshaOriginalData {
        @Schema(description = "이름")
        private String name;
        @Schema(description = "생년월일")
        private String birthDate;
        @Schema(description = "등록번호")
        private String registrationNumber;
        @Schema(description = "전화번호")
        private String phoneNumber;
        @Schema(description = "사진 파일 번호")
        private String photoFileNo;
        @Schema(description = "파일 시퀀스")
        private String fileSeq;
        @Schema(description = "교육명")
        private String eduName;
        @Schema(description = "교육일자")
        private String eduDate;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBirthDate() { return birthDate; }
        public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
        public String getRegistrationNumber() { return registrationNumber; }
        public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getPhotoFileNo() { return photoFileNo; }
        public void setPhotoFileNo(String photoFileNo) { this.photoFileNo = photoFileNo; }
        public String getFileSeq() { return fileSeq; }
        public void setFileSeq(String fileSeq) { this.fileSeq = fileSeq; }
        public String getEduName() { return eduName; }
        public void setEduName(String eduName) { this.eduName = eduName; }
        public String getEduDate() { return eduDate; }
        public void setEduDate(String eduDate) { this.eduDate = eduDate; }
    }
}
