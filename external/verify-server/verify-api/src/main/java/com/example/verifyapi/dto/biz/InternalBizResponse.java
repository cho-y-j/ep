package com.example.verifyapi.dto.biz;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalBizResponse {

    private String result;
    private String reasonCode;
    private String message;
    private String provider;
    private String verifiedAt;
    private Object raw;

    public InternalBizResponse() {}

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

    public static InternalBizResponse valid(String message) {
        InternalBizResponse response = new InternalBizResponse();
        response.setResult("VALID");
        response.setReasonCode("SUCCESS");
        response.setMessage(message);
        response.setProvider("NTS_API");
        return response;
    }

    public static InternalBizResponse invalid(String message) {
        InternalBizResponse response = new InternalBizResponse();
        response.setResult("INVALID");
        response.setReasonCode("NOT_MATCHED");
        response.setMessage(message);
        response.setProvider("NTS_API");
        return response;
    }

    public static InternalBizResponse unknown(String reasonCode, String message) {
        InternalBizResponse response = new InternalBizResponse();
        response.setResult("UNKNOWN");
        response.setReasonCode(reasonCode);
        response.setMessage(message);
        response.setProvider("NTS_API");
        return response;
    }
}
