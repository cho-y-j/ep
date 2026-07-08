package com.example.verifyapi.rims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RIMS 운전면허 배치 검증 응답 항목 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RimsBatchVerifyItem {

    private int index;
    private String licenseNo;
    private String residentName;
    private String result;
    private String reasonCode;
    private Object raw;

    public RimsBatchVerifyItem() {}

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public String getResidentName() {
        return residentName;
    }

    public void setResidentName(String residentName) {
        this.residentName = residentName;
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

    public Object getRaw() {
        return raw;
    }

    public void setRaw(Object raw) {
        this.raw = raw;
    }

    public static RimsBatchVerifyItem valid(int index, String licenseNo, String residentName) {
        RimsBatchVerifyItem item = new RimsBatchVerifyItem();
        item.setIndex(index);
        item.setLicenseNo(licenseNo);
        item.setResidentName(residentName);
        item.setResult("VALID");
        item.setReasonCode("SUCCESS");
        return item;
    }

    public static RimsBatchVerifyItem invalid(int index, String licenseNo, String residentName, String reasonCode) {
        RimsBatchVerifyItem item = new RimsBatchVerifyItem();
        item.setIndex(index);
        item.setLicenseNo(licenseNo);
        item.setResidentName(residentName);
        item.setResult("INVALID");
        item.setReasonCode(reasonCode);
        return item;
    }

    public static RimsBatchVerifyItem unknown(int index, String licenseNo, String residentName, String reasonCode) {
        RimsBatchVerifyItem item = new RimsBatchVerifyItem();
        item.setIndex(index);
        item.setLicenseNo(licenseNo);
        item.setResidentName(residentName);
        item.setResult("UNKNOWN");
        item.setReasonCode(reasonCode);
        return item;
    }
}
