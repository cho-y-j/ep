package com.example.verifyapi.util;

/**
 * 텍스트 정규화 유틸리티
 *
 * [중요] 이 구현은 "공식 KOSHA API 연동"이 아니라
 * KOSHA QR 조회 웹 절차를 서버에서 자동화/대행하는 구조이다.
 *
 * OCR 추출 데이터와 KOSHA 원본 데이터 비교를 위한 정규화 규칙:
 * - 이름: 공백/개행 제거
 * - 생년월일: YYYYMMDD 형식으로 통일
 * - 등록번호: 하이픈 제거
 */
public final class TextNormalizer {

    private TextNormalizer() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * 이름 정규화
     * - 공백, 개행, 탭 제거
     * - 앞뒤 공백 제거
     */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.replaceAll("[\\s\\n\\r\\t]+", "").trim();
    }

    /**
     * 생년월일 정규화
     * - YYYYMMDD 형식으로 통일
     * - 구분자(., -, /) 제거
     * - 숫자만 추출
     */
    public static String normalizeBirthDate(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) {
            return null;
        }
        // 숫자만 추출
        String digits = birthDate.replaceAll("[^0-9]", "");

        // 8자리가 아니면 원본 반환 (비교 시 불일치로 처리됨)
        if (digits.length() != 8) {
            return digits;
        }
        return digits;
    }

    /**
     * 등록번호 정규화
     * - 하이픈 제거
     * - 공백 제거
     * - 대문자로 통일
     */
    public static String normalizeRegistrationNumber(String regNum) {
        if (regNum == null || regNum.isBlank()) {
            return null;
        }
        return regNum.replaceAll("[\\-\\s]", "").toUpperCase().trim();
    }

    /**
     * 전화번호 정규화
     * - 숫자만 추출
     */
    public static String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }

    /**
     * 두 문자열 비교 (정규화 후)
     * - 둘 다 null이면 비교 불가 (null 반환)
     * - 하나만 null이면 비교 불가 (null 반환)
     * - 둘 다 값이 있으면 비교 결과 반환
     */
    public static Boolean compareNormalized(String value1, String value2) {
        if (value1 == null || value2 == null) {
            return null; // 비교 불가
        }
        if (value1.isBlank() || value2.isBlank()) {
            return null; // 비교 불가
        }
        return value1.equals(value2);
    }
}
