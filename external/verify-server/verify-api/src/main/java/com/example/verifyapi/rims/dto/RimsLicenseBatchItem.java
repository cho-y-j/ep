package com.example.verifyapi.rims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RIMS 운전면허 배치 검증 요청 항목 DTO
 * - NON_EMPTY: null 또는 빈 문자열인 필드는 직렬화에서 제외
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RimsLicenseBatchItem {

    @NotBlank(message = "운전면허번호는 필수입니다")
    @JsonProperty("f_license_no")
    private String licenseNo;

    @NotBlank(message = "성명은 필수입니다")
    @Size(min = 1, max = 50, message = "성명은 1~50자여야 합니다")
    @JsonProperty("f_resident_name")
    private String residentName;

    @NotBlank(message = "면허종별코드는 필수입니다")
    @Pattern(regexp = "\\d{2}", message = "면허종별코드는 2자리 숫자여야 합니다")
    @JsonProperty("f_licn_con_code")
    private String licenseConditionCode;

    @Pattern(regexp = "\\d{8}", message = "조회시작일자는 YYYYMMDD 형식이어야 합니다")
    @JsonProperty("f_from_date")
    private String fromDate;

    @Pattern(regexp = "\\d{8}", message = "조회종료일자는 YYYYMMDD 형식이어야 합니다")
    @JsonProperty("f_to_date")
    private String toDate;

    @Size(max = 20, message = "차량등록번호는 20자 이하여야 합니다")
    @JsonProperty("vhcl_reg_no")
    private String vehicleRegNo;

    public RimsLicenseBatchItem() {}

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

    public String getLicenseConditionCode() {
        return licenseConditionCode;
    }

    public void setLicenseConditionCode(String licenseConditionCode) {
        this.licenseConditionCode = licenseConditionCode;
    }

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setToDate(String toDate) {
        this.toDate = toDate;
    }

    public String getVehicleRegNo() {
        return vehicleRegNo;
    }

    public void setVehicleRegNo(String vehicleRegNo) {
        this.vehicleRegNo = vehicleRegNo;
    }
}
