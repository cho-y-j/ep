package com.example.mainapi.dto.carstat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "자동차 성능점검 통계 조회 요청")
public class CarPerformanceRequest {

    @Schema(description = "등록년도 (4자리)", example = "2024", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "등록년도(registYy)는 필수입니다")
    @Pattern(regexp = "^\\d{4}$", message = "등록년도는 4자리 숫자여야 합니다")
    private String registYy;

    @Schema(description = "등록월 (1~12)", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "등록월(registMt)은 필수입니다")
    @Pattern(regexp = "^(0?[1-9]|1[0-2])$", message = "등록월은 1~12 사이의 값이어야 합니다")
    private String registMt;

    @Schema(description = "차종코드 (1:승용, 2:승합, 3:화물, 4:특수)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "차종코드(vhctyAsortCode)는 필수입니다")
    @Min(value = 1, message = "차종코드는 1~4 사이의 값이어야 합니다")
    @Max(value = 4, message = "차종코드는 1~4 사이의 값이어야 합니다")
    private Integer vhctyAsortCode;

    @Schema(description = "지역코드 (1~17)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "지역코드(registGrcCode)는 필수입니다")
    @Min(value = 1, message = "지역코드는 1~17 사이의 값이어야 합니다")
    @Max(value = 17, message = "지역코드는 1~17 사이의 값이어야 합니다")
    private Integer registGrcCode;

    @Schema(description = "모델년도 (4자리)", example = "2020", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "모델년도(prye)는 필수입니다")
    @Pattern(regexp = "^\\d{4}$", message = "모델년도는 4자리 숫자여야 합니다")
    private String prye;

    public CarPerformanceRequest() {}

    public CarPerformanceRequest(String registYy, String registMt, Integer vhctyAsortCode,
                                 Integer registGrcCode, String prye) {
        this.registYy = registYy;
        this.registMt = registMt;
        this.vhctyAsortCode = vhctyAsortCode;
        this.registGrcCode = registGrcCode;
        this.prye = prye;
    }

    public String getRegistYy() {
        return registYy;
    }

    public void setRegistYy(String registYy) {
        this.registYy = registYy;
    }

    public String getRegistMt() {
        return registMt;
    }

    public void setRegistMt(String registMt) {
        this.registMt = registMt;
    }

    public Integer getVhctyAsortCode() {
        return vhctyAsortCode;
    }

    public void setVhctyAsortCode(Integer vhctyAsortCode) {
        this.vhctyAsortCode = vhctyAsortCode;
    }

    public Integer getRegistGrcCode() {
        return registGrcCode;
    }

    public void setRegistGrcCode(Integer registGrcCode) {
        this.registGrcCode = registGrcCode;
    }

    public String getPrye() {
        return prye;
    }

    public void setPrye(String prye) {
        this.prye = prye;
    }
}
