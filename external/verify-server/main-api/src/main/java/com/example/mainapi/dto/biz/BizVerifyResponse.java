package com.example.mainapi.dto.biz;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사업자등록정보 진위확인 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BizVerifyResponse {

    @Schema(description = "요청 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String requestId;

    @Schema(description = "검증 결과", example = "VALID", allowableValues = {"VALID", "INVALID", "UNKNOWN"})
    private String result;

    @Schema(description = "결과 코드", example = "SUCCESS")
    private String reasonCode;

    @Schema(description = "결과 메시지", example = "확인됨")
    private String message;

    @Schema(description = "제공자", example = "NTS_API")
    private String provider;

    @Schema(description = "검증 시각", example = "2024-01-15T10:30:00")
    private String verifiedAt;

    @Schema(description = "원본 응답 (선택)")
    private Object raw;

    public BizVerifyResponse() {}

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(String verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Object getRaw() {
        return raw;
    }

    public void setRaw(Object raw) {
        this.raw = raw;
    }
}
