package com.example.verifyapi.rims.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * RIMS 운전면허 배치 검증 요청 DTO
 * - NON_EMPTY: null 또는 빈 문자열인 필드는 직렬화에서 제외
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RimsLicenseBatchRequest {

    @NotNull(message = "전송건수는 필수입니다")
    @JsonProperty("f_send_cnt")
    private Integer sendCount;

    @Size(max = 32, message = "사업자정보는 32자 이하여야 합니다")
    @JsonProperty("bizinfo")
    private String bizInfo;

    @NotEmpty(message = "요청 목록은 비어있을 수 없습니다")
    @Size(max = 1000, message = "배치 요청은 최대 1000건까지 가능합니다")
    @Valid
    @JsonProperty("requestList")
    private List<RimsLicenseBatchItem> requestList;

    public RimsLicenseBatchRequest() {}

    public Integer getSendCount() {
        return sendCount;
    }

    public void setSendCount(Integer sendCount) {
        this.sendCount = sendCount;
    }

    public String getBizInfo() {
        return bizInfo;
    }

    public void setBizInfo(String bizInfo) {
        this.bizInfo = bizInfo;
    }

    public List<RimsLicenseBatchItem> getRequestList() {
        return requestList;
    }

    public void setRequestList(List<RimsLicenseBatchItem> requestList) {
        this.requestList = requestList;
    }

    @AssertTrue(message = "전송건수와 요청 목록 크기가 일치해야 합니다")
    private boolean isSendCountValid() {
        if (sendCount == null || requestList == null) {
            return true;
        }
        return sendCount == requestList.size();
    }
}
