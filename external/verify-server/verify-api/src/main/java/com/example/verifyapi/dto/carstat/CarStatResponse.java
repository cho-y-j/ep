package com.example.verifyapi.dto.carstat;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 자동차 통계 조회 공통 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CarStatResponse {

    private String result;
    private String reasonCode;
    private String message;
    private String provider;
    private String verifiedAt;
    private Integer dtaCo;
    private Object raw;

    public CarStatResponse() {}

    public static CarStatResponse success(Integer dtaCo) {
        CarStatResponse response = new CarStatResponse();
        response.setResult("SUCCESS");
        response.setReasonCode("00");
        response.setMessage("조회 성공");
        response.setDtaCo(dtaCo);
        return response;
    }

    public static CarStatResponse error(String reasonCode, String message) {
        CarStatResponse response = new CarStatResponse();
        response.setResult("ERROR");
        response.setReasonCode(reasonCode);
        response.setMessage(message);
        return response;
    }

    public static CarStatResponse unknown(String reasonCode, String message) {
        CarStatResponse response = new CarStatResponse();
        response.setResult("UNKNOWN");
        response.setReasonCode(reasonCode);
        response.setMessage(message);
        return response;
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
