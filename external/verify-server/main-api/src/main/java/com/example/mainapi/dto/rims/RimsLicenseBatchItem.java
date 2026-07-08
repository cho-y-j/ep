package com.example.mainapi.dto.rims;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RIMS 운전면허 배치 검증 요청 항목 DTO (main-api)
 */
@Schema(description = "운전면허 배치 검증 요청 항목")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RimsLicenseBatchItem {

    @Schema(
            description = "운전면허번호 (12자리 숫자 또는 지역명 포함 형식). " +
                    "지역명 형식(서울12-345678-90)도 자동 변환됨",
            example = "110123456789",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "운전면허번호는 필수입니다")
    @JsonProperty("f_license_no")
    private String licenseNo;

    @Schema(description = "성명", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "성명은 필수입니다")
    @Size(min = 1, max = 50, message = "성명은 1~50자여야 합니다")
    @JsonProperty("f_resident_name")
    private String residentName;

    @Schema(
            description = "면허종별코드 (2자리). " +
                    "11:1종대형, 12:1종보통, 13:1종소형, " +
                    "21:2종보통, 22:2종소형, 23:2종원동기",
            example = "12",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "면허종별코드는 필수입니다")
    @Pattern(regexp = "\\d{2}", message = "면허종별코드는 2자리 숫자여야 합니다")
    @JsonProperty("f_licn_con_code")
    private String licenseConditionCode;

    @Schema(description = "조회시작일자 (YYYYMMDD, 미입력시 오늘)", example = "20260116")
    @Pattern(regexp = "\\d{8}", message = "조회시작일자는 YYYYMMDD 형식이어야 합니다")
    @JsonProperty("f_from_date")
    private String fromDate;

    @Schema(description = "조회종료일자 (YYYYMMDD, 미입력시 오늘)", example = "20261231")
    @Pattern(regexp = "\\d{8}", message = "조회종료일자는 YYYYMMDD 형식이어야 합니다")
    @JsonProperty("f_to_date")
    private String toDate;

    @Schema(description = "차량등록번호 (선택)", example = "12가3456")
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
