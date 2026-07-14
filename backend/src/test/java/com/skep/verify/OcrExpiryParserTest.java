package com.skep.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 실데이터(차량등록증 paddle OCR)에서 검사유효기간 백필이 실패했던 회귀를 잡는다.
 * 원인: paddle 읽기순서상 앵커 '검사유효기간'과 날짜 범위가 표 레이아웃 탓에 ~180자 떨어져
 * 기존 40자 window 로는 미검출됐고, 넘겨도 시작일을 잡았음. → 범위 끝날짜 캡처 + window 300.
 */
class OcrExpiryParserTest {

    // 실제 paddle fullText 발췌 — 앵커와 날짜 범위(2025-05-20 2026-05-19)가 여러 줄 떨어져 있다.
    private static final String VEHICLE_REAL = """
            1.제원 4.검사유효기간
            ①제원관리번호 (형식승인번호) B8W-1-02040-0000-3325 30 3 ③ ③3 ③4 ③5
            12길이 12745mm 1 너비 2495 mm 연월 연월 검사 주행 책임자 검사 검사
            1④ 높이 3840 mm 1총중량 22440 kg 일부터 일까지 시행장소 거리 확인 구분
            16승차정원 2 명 ①최대적재량 3600 kg 2025-05-20 2026-05-19
            18배기량또는 구동축전지 용량 9960""";

    @Test
    void vehicleRange_realData_capturesEndDate() {
        assertEquals(Optional.of(LocalDate.of(2026, 5, 19)),
                OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", VEHICLE_REAL));
    }

    @Test
    void tildeRange_capturesEndDate() {
        assertEquals(Optional.of(LocalDate.of(2026, 5, 19)),
                OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", "검사유효기간 2025.05.20 ~ 2026.05.19"));
    }

    @Test
    void singleExpiry_fallback() {
        assertEquals(Optional.of(LocalDate.of(2027, 3, 15)),
                OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", "정기검사 만료일 2027-03-15 검사 완료"));
    }

    @Test
    void license_adaptationPeriod() {
        assertEquals(Optional.of(LocalDate.of(2028, 3, 10)),
                OcrExpiryParser.parse("LICENSE", "성명 홍길동 적성검사기간 2028-03-10 까지"));
    }

    @Test
    void noAnchor_empty() {
        assertTrue(OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", "차량번호 89러3500 최초등록일 2025-05-20").isEmpty());
    }

    @Test
    void nullEmpty_guard() {
        assertTrue(OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", null).isEmpty());
        assertTrue(OcrExpiryParser.parse(null, "검사유효기간 2026-05-19").isEmpty());
        assertTrue(OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", "   ").isEmpty());
    }

    @Test
    void insaneYear_rejected() {
        assertTrue(OcrExpiryParser.parse("EQUIPMENT_REGISTRATION", "검사유효기간 1980-01-01 1990-01-01").isEmpty());
    }
}
