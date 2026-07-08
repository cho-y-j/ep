package com.example.verifyapi.rims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * RIMS 운전면허 검증 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RimsVerifyResponse {

    private static final String PROVIDER = "RIMS";

    private String result;
    private String reasonCode;
    private String provider;
    private String verifiedAt;
    private Object raw;

    public RimsVerifyResponse() {
        this.provider = PROVIDER;
        this.verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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

    public static RimsVerifyResponse valid() {
        RimsVerifyResponse response = new RimsVerifyResponse();
        response.setResult("VALID");
        response.setReasonCode("SUCCESS");
        return response;
    }

    public static RimsVerifyResponse invalid(String reasonCode) {
        RimsVerifyResponse response = new RimsVerifyResponse();
        response.setResult("INVALID");
        response.setReasonCode(reasonCode);
        return response;
    }

    public static RimsVerifyResponse unknown(String reasonCode) {
        RimsVerifyResponse response = new RimsVerifyResponse();
        response.setResult("UNKNOWN");
        response.setReasonCode(reasonCode);
        return response;
    }
}
