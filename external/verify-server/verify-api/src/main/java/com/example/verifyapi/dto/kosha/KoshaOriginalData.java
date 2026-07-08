package com.example.verifyapi.dto.kosha;

/**
 * KOSHA 포털 조회 결과 (원본 데이터)
 * - trneInfo 응답에서 파싱한 교육이수 정보
 * - 이 데이터가 존재하면 1차 인증(조회 성공) 통과
 */
public class KoshaOriginalData {

    private String name;
    private String birthDate;
    private String registrationNumber;
    private String phoneNumber;
    private String photoFileNo;
    private String fileSeq;
    private String eduName;
    private String eduDate;

    public KoshaOriginalData() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhotoFileNo() {
        return photoFileNo;
    }

    public void setPhotoFileNo(String photoFileNo) {
        this.photoFileNo = photoFileNo;
    }

    public String getFileSeq() {
        return fileSeq;
    }

    public void setFileSeq(String fileSeq) {
        this.fileSeq = fileSeq;
    }

    public String getEduName() {
        return eduName;
    }

    public void setEduName(String eduName) {
        this.eduName = eduName;
    }

    public String getEduDate() {
        return eduDate;
    }

    public void setEduDate(String eduDate) {
        this.eduDate = eduDate;
    }

    @Override
    public String toString() {
        return "KoshaOriginalData{name='" + name + "', birthDate='" + birthDate +
               "', registrationNumber='" + registrationNumber + "', eduName='" + eduName + "'}";
    }
}
