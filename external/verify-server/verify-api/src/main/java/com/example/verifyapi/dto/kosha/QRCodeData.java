package com.example.verifyapi.dto.kosha;

/**
 * QR 코드에서 추출한 데이터
 */
public class QRCodeData {

    private String q;
    private String ptSignature;
    private String url;

    public QRCodeData() {}

    public QRCodeData(String q, String ptSignature, String url) {
        this.q = q;
        this.ptSignature = ptSignature;
        this.url = url;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getPtSignature() {
        return ptSignature;
    }

    public void setPtSignature(String ptSignature) {
        this.ptSignature = ptSignature;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isReadyForVerification() {
        return q != null && !q.isBlank() && ptSignature != null && !ptSignature.isBlank();
    }

    @Override
    public String toString() {
        return "QRCodeData{q='" + q + "', ptSignature='" + (ptSignature != null ? "[PRESENT]" : "null") + "', url='" + url + "'}";
    }
}
