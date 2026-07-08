package com.example.verifyapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalCargoResponse {

    private String result;
    private String reasonCode;
    private String provider;
    private String verifiedAt;
    private Object raw;

    public InternalCargoResponse() {}

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

    public static InternalCargoResponse success() {
        InternalCargoResponse response = new InternalCargoResponse();
        response.setResult("VALID");
        response.setReasonCode("SUCCESS");
        response.setProvider("PUBLIC_API");
        return response;
    }

    public static InternalCargoResponse invalid() {
        InternalCargoResponse response = new InternalCargoResponse();
        response.setResult("INVALID");
        response.setReasonCode("NOT_FOUND");
        response.setProvider("PUBLIC_API");
        return response;
    }

    public static InternalCargoResponse unknown(String reasonCode) {
        InternalCargoResponse response = new InternalCargoResponse();
        response.setResult("UNKNOWN");
        response.setReasonCode(reasonCode);
        response.setProvider("PUBLIC_API");
        return response;
    }
}
