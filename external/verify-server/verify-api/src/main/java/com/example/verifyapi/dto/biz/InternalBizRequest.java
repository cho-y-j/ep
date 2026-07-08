package com.example.verifyapi.dto.biz;

public class InternalBizRequest {

    private String bizNo;
    private String startDate;
    private String ownerName;
    private String ownerName2;
    private String bizName;
    private String corpNo;
    private String bizSector;
    private String bizType;

    public InternalBizRequest() {}

    public InternalBizRequest(String bizNo, String startDate, String ownerName) {
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
