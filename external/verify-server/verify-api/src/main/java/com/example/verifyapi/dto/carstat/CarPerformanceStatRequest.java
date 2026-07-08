package com.example.verifyapi.dto.carstat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 자동차 성능점검 통계 조회 요청 DTO (verify-api 내부용)
 */
public class CarPerformanceStatRequest {

    @NotNull(message = "등록년도(registYy)는 필수입니다")
    @Pattern(regexp = "^\\d{4}$", message = "등록년도는 4자리 숫자여야 합니다")
    private String registYy;

    @NotNull(message = "등록월(registMt)은 필수입니다")
    @Pattern(regexp = "^(0?[1-9]|1[0-2])$", message = "등록월은 1~12 사이의 값이어야 합니다")
    private String registMt;

    @NotNull(message = "차종코드(vhctyAsortCode)는 필수입니다")
    @Min(value = 1, message = "차종코드는 1~4 사이의 값이어야 합니다")
    @Max(value = 4, message = "차종코드는 1~4 사이의 값이어야 합니다")
    private Integer vhctyAsortCode;

    @NotNull(message = "지역코드(registGrcCode)는 필수입니다")
    @Min(value = 1, message = "지역코드는 1~17 사이의 값이어야 합니다")
    @Max(value = 17, message = "지역코드는 1~17 사이의 값이어야 합니다")
    private Integer registGrcCode;

    @NotNull(message = "모델년도(prye)는 필수입니다")
    @Pattern(regexp = "^\\d{4}$", message = "모델년도는 4자리 숫자여야 합니다")
    private String prye;

    public CarPerformanceStatRequest() {}

    public CarPerformanceStatRequest(String registYy, String registMt, Integer vhctyAsortCode,
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
