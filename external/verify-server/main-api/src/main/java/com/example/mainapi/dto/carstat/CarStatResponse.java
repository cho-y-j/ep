package com.example.mainapi.dto.carstat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자동차 통계 조회 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CarStatResponse {

    @Schema(description = "요청 ID")
    private String requestId;

    @Schema(description = "결과 (SUCCESS/ERROR/UNKNOWN)")
    private String result;

    @Schema(description = "결과 코드")
    private String reasonCode;

    @Schema(description = "메시지")
    private String message;

    @Schema(description = "데이터 제공자")
    private String provider;

    @Schema(description = "조회 시각")
    private String verifiedAt;

    @Schema(description = "데이터 건수")
    private Integer dtaCo;

    @Schema(description = "원본 응답")
    private Object raw;

    public CarStatResponse() {}

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

    public Integer getDtaCo() {
        return dtaCo;
    }

    public void setDtaCo(Integer dtaCo) {
        this.dtaCo = dtaCo;
    }

    public Object getRaw() {
        return raw;
    }

    public void setRaw(Object raw) {
        this.raw = raw;
    }
}
