package com.example.verifyapi.rims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RIMS 운전면허 배치 검증 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RimsBatchVerifyResponse {

    private static final String PROVIDER = "RIMS";

    private String provider;
    private String verifiedAt;
    private int totalCount;
    private int validCount;
    private int invalidCount;
    private int unknownCount;
    private List<RimsBatchVerifyItem> results;

    public RimsBatchVerifyResponse() {
        this.provider = PROVIDER;
        this.verifiedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
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

    public List<RimsBatchVerifyItem> getResults() {
        return results;
    }

    public void setResults(List<RimsBatchVerifyItem> results) {
        this.results = results;
    }
}
