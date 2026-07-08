package com.example.mainapi.dto.rims;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RIMS 운전면허 배치 검증 응답 항목 DTO (main-api)
 */
@Schema(description = "운전면허 배치 검증 응답 항목")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RimsLicenseBatchResultItem {

    @Schema(description = "항목 인덱스", example = "0")
    private int index;

    @Schema(description = "운전면허번호", example = "123456789012")
    private String licenseNo;

    @Schema(description = "성명", example = "홍길동")
    private String residentName;

    @Schema(description = "검증 결과", example = "VALID", allowableValues = {"VALID", "INVALID", "UNKNOWN"})
    private String result;

    @Schema(description = "결과 코드", example = "SUCCESS")
    private String reasonCode;

    @Schema(description = "원본 응답 (선택)")
    private Object raw;

    public RimsLicenseBatchResultItem() {}

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
}
