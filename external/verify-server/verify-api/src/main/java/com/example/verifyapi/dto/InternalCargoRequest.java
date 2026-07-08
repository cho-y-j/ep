package com.example.verifyapi.dto;

public class InternalCargoRequest {

    private String name;
    private String birth;
    private String lcnsNo;
    private String area;

    public InternalCargoRequest() {}

    public InternalCargoRequest(String name, String birth, String lcnsNo, String area) {
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
