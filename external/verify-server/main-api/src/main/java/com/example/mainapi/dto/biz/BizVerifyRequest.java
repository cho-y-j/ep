package com.example.mainapi.dto.biz;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "사업자등록정보 진위확인 요청")
public class BizVerifyRequest {

    @Schema(description = "사업자등록번호 (10자리, 하이픈 제외)", example = "1234567890", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "사업자등록번호는 필수입니다")
    @Pattern(regexp = "^\\d{10}$", message = "사업자등록번호는 10자리 숫자여야 합니다")
    private String bizNo;

    @Schema(description = "개업일자 (YYYYMMDD)", example = "20200101", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "개업일자는 필수입니다")
    @Pattern(regexp = "^\\d{8}$", message = "개업일자는 YYYYMMDD 형식이어야 합니다")
    private String startDate;

    @Schema(description = "대표자명", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "대표자명은 필수입니다")
    @Size(min = 2, max = 30, message = "대표자명은 2자 이상 30자 이하여야 합니다")
    private String ownerName;

    @Schema(description = "대표자명2 (공동대표 등)", example = "김철수")
    @Size(max = 30, message = "대표자명2는 30자 이하여야 합니다")
    private String ownerName2;

    @Schema(description = "상호명", example = "주식회사 테스트")
    @Size(max = 100, message = "상호명은 100자 이하여야 합니다")
    private String bizName;

    @Schema(description = "법인등록번호 (13자리, 하이픈 제외)", example = "1234567890123")
    @Pattern(regexp = "^(\\d{13})?$", message = "법인등록번호는 13자리 숫자여야 합니다")
    private String corpNo;

    @Schema(description = "주업태명", example = "도매 및 소매업")
    @Size(max = 100, message = "주업태명은 100자 이하여야 합니다")
    private String bizSector;

    @Schema(description = "주종목명", example = "전자상거래")
    @Size(max = 100, message = "주종목명은 100자 이하여야 합니다")
    private String bizType;

    public BizVerifyRequest() {}

    public BizVerifyRequest(String bizNo, String startDate, String ownerName) {
        this.bizNo = bizNo;
        this.startDate = startDate;
        this.ownerName = ownerName;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerName2() {
        return ownerName2;
    }

    public void setOwnerName2(String ownerName2) {
        this.ownerName2 = ownerName2;
    }

    public String getBizName() {
        return bizName;
    }

    public void setBizName(String bizName) {
        this.bizName = bizName;
    }

    public String getCorpNo() {
        return corpNo;
    }

    public void setCorpNo(String corpNo) {
        this.corpNo = corpNo;
    }

    public String getBizSector() {
        return bizSector;
    }

    public void setBizSector(String bizSector) {
        this.bizSector = bizSector;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }
}
