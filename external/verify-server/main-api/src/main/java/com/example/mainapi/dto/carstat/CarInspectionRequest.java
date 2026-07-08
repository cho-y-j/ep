package com.example.mainapi.dto.carstat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "자동차 검사정보 통계 조회 요청")
public class CarInspectionRequest {

    @Schema(description = "시작일 (YYYYMMDD)", example = "20240101", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "시작일(bgnde)은 필수입니다")
    @Pattern(regexp = "^\\d{8}$", message = "시작일은 YYYYMMDD 형식이어야 합니다")
    private String bgnde;

    @Schema(description = "종료일 (YYYYMMDD)", example = "20240630", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "종료일(endde)은 필수입니다")
    @Pattern(regexp = "^\\d{8}$", message = "종료일은 YYYYMMDD 형식이어야 합니다")
    private String endde;

    @Schema(description = "법정동코드 (10자리, 선택)", example = "1100000000")
    @Pattern(regexp = "^\\d{10}$", message = "법정동코드는 10자리 숫자여야 합니다")
    private String useStrnghldLegaldongCode;

    @Schema(description = "용도 (자가용/영업용/관용, 선택)", example = "자가용")
    @Pattern(regexp = "^(자가용|영업용|관용)$", message = "용도는 자가용/영업용/관용 중 하나여야 합니다")
    private String prposSeNm;

    @Schema(description = "차종 (승용/승합/특수/화물, 선택)", example = "승용")
    @Pattern(regexp = "^(승용|승합|특수|화물)$", message = "차종은 승용/승합/특수/화물 중 하나여야 합니다")
    private String vhctyAsortNm;

    @Schema(description = "세부차종 (경형/소형/중형/대형, 선택)", example = "중형")
    @Pattern(regexp = "^(경형|소형|중형|대형)$", message = "세부차종은 경형/소형/중형/대형 중 하나여야 합니다")
    private String vhctyClNm;

    public CarInspectionRequest() {}

    public CarInspectionRequest(String bgnde, String endde) {
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
