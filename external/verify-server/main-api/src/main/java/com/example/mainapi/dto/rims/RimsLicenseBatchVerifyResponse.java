package com.example.mainapi.dto.rims;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * RIMS 운전면허 배치 검증 응답 DTO (main-api)
 */
@Schema(description = "운전면허 배치 검증 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RimsLicenseBatchVerifyResponse {

    @Schema(description = "요청 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String requestId;

    @Schema(description = "제공자", example = "RIMS")
    private String provider;

    @Schema(description = "검증 시각", example = "2024-01-15T10:30:00")
    private String verifiedAt;

    @Schema(description = "총 건수")
    private int totalCount;

    @Schema(description = "적격 건수")
    private int validCount;

    @Schema(description = "부적격 건수")
    private int invalidCount;

    @Schema(description = "판정불가 건수")
    private int unknownCount;

    @Schema(description = "검증 결과 목록")
    private List<RimsLicenseBatchResultItem> results;

    public RimsLicenseBatchVerifyResponse() {}

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(String verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getValidCount() {
        return validCount;
    }

    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }

    public int getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(int invalidCount) {
        this.invalidCount = invalidCount;
    }

    public int getUnknownCount() {
        return unknownCount;
    }

    public void setUnknownCount(int unknownCount) {
        this.unknownCount = unknownCount;
    }

    public List<RimsLicenseBatchResultItem> getResults() {
        return results;
    }

    public void setResults(List<RimsLicenseBatchResultItem> results) {
        this.results = results;
    }
}
