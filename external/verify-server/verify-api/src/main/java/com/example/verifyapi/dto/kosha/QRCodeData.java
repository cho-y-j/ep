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

    /**
     * KOSHA 조회 가능 여부.
     * 2026-07 실측: 신규 포털(cobedu) selectTrneInfo 는 q 단독으로 조회 성공 —
     * QR 랜딩이 SPA 로 바뀌어 ptSignature 를 HTML 에서 얻을 수 없는 경우가 있어 q 만 필수로 한다.
     */
    public boolean isReadyForVerification() {
        return q != null && !q.isBlank();
    }

    @Override
    public String toString() {
        return "QRCodeData{q='" + q + "', ptSignature='" + (ptSignature != null ? "[PRESENT]" : "null") + "', url='" + url + "'}";
    }
}
