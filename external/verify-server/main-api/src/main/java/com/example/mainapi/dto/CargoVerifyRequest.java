package com.example.mainapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "화물운송 자격증 진위여부 검증 요청")
public class CargoVerifyRequest {

    @Schema(description = "성명", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "성명은 필수입니다")
    @Size(min = 2, max = 20, message = "성명은 2자 이상 20자 이하여야 합니다")
    private String name;

    @Schema(description = "생년월일 (YYYY-MM-DD)", example = "1990-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "생년월일은 필수입니다")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "생년월일 형식은 YYYY-MM-DD여야 합니다")
    private String birth;

    @Schema(description = "자격증번호 (하이픈(-)은 제외)", example = "12345678", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "자격증번호는 필수입니다")
    @Size(min = 5, max = 30, message = "자격증번호는 5자 이상 30자 이하여야 합니다")
    private String lcnsNo;

    @Schema(description = "지역 (선택)", example = "서울")
    private String area;

    public CargoVerifyRequest() {}

    public CargoVerifyRequest(String name, String birth, String lcnsNo, String area) {
        this.name = name;
        this.birth = birth;
        this.lcnsNo = lcnsNo;
        this.area = area;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirth() {
        return birth;
    }

    public void setBirth(String birth) {
        this.birth = birth;
    }

    public String getLcnsNo() {
        return lcnsNo;
    }

    public void setLcnsNo(String lcnsNo) {
        this.lcnsNo = lcnsNo;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }
}
