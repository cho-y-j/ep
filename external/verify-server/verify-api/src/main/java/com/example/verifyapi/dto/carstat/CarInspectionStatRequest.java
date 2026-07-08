package com.example.verifyapi.dto.carstat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 자동차 검사정보 통계 조회 요청 DTO (verify-api 내부용)
 */
public class CarInspectionStatRequest {

    @NotNull(message = "시작일(bgnde)은 필수입니다")
    @Pattern(regexp = "^\\d{8}$", message = "시작일은 YYYYMMDD 형식이어야 합니다")
    private String bgnde;

    @NotNull(message = "종료일(endde)은 필수입니다")
    @Pattern(regexp = "^\\d{8}$", message = "종료일은 YYYYMMDD 형식이어야 합니다")
    private String endde;

    @Pattern(regexp = "^\\d{10}$", message = "법정동코드는 10자리 숫자여야 합니다")
    private String useStrnghldLegaldongCode;

    @Pattern(regexp = "^(자가용|영업용|관용)$", message = "용도는 자가용/영업용/관용 중 하나여야 합니다")
    private String prposSeNm;

    @Pattern(regexp = "^(승용|승합|특수|화물)$", message = "차종은 승용/승합/특수/화물 중 하나여야 합니다")
    private String vhctyAsortNm;

    @Pattern(regexp = "^(경형|소형|중형|대형)$", message = "세부차종은 경형/소형/중형/대형 중 하나여야 합니다")
    private String vhctyClNm;

    public CarInspectionStatRequest() {}

    public CarInspectionStatRequest(String bgnde, String endde) {
        this.bgnde = bgnde;
        this.endde = endde;
    }

    public String getBgnde() {
        return bgnde;
    }

    public void setBgnde(String bgnde) {
        this.bgnde = bgnde;
    }

    public String getEndde() {
        return endde;
    }

    public void setEndde(String endde) {
        this.endde = endde;
    }

    public String getUseStrnghldLegaldongCode() {
        return useStrnghldLegaldongCode;
    }

    public void setUseStrnghldLegaldongCode(String useStrnghldLegaldongCode) {
        this.useStrnghldLegaldongCode = useStrnghldLegaldongCode;
    }

    public String getPrposSeNm() {
        return prposSeNm;
    }

    public void setPrposSeNm(String prposSeNm) {
        this.prposSeNm = prposSeNm;
    }

    public String getVhctyAsortNm() {
        return vhctyAsortNm;
    }

    public void setVhctyAsortNm(String vhctyAsortNm) {
        this.vhctyAsortNm = vhctyAsortNm;
    }

    public String getVhctyClNm() {
        return vhctyClNm;
    }

    public void setVhctyClNm(String vhctyClNm) {
        this.vhctyClNm = vhctyClNm;
    }
}
