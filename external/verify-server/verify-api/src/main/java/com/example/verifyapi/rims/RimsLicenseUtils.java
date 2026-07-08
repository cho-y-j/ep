package com.example.verifyapi.rims;

import java.util.Map;

/**
 * RIMS 운전면허 유틸리티
 * - 지역명 → 지역코드 변환
 * - 면허번호 정규화 (12자리)
 */
public final class RimsLicenseUtils {

    private RimsLicenseUtils() {}

    /**
     * 지역명 → 지역코드 매핑 (RIMS 규격)
     */
    private static final Map<String, String> REGION_CODE_MAP = Map.ofEntries(
            Map.entry("서울", "11"),
            Map.entry("부산", "12"),
            Map.entry("경기", "13"),
            Map.entry("강원", "14"),
            Map.entry("충북", "15"),
            Map.entry("충남", "16"),
            Map.entry("전북", "17"),
            Map.entry("전남", "18"),
            Map.entry("경북", "19"),
            Map.entry("경남", "20"),
            Map.entry("제주", "21"),
            Map.entry("대구", "22"),
            Map.entry("인천", "23"),
            Map.entry("광주", "24"),
            Map.entry("대전", "25"),
            Map.entry("울산", "26")
    );

    /**
     * 면허번호 정규화 (지역명 → 지역코드 변환)
     * - "서울12-345678-90" → "111234567890"
     * - "경북18-002122-11" → "191800212211"
     * - 이미 숫자로만 구성된 경우 그대로 반환
     *
     * @param licenseNo 원본 면허번호
     * @return 정규화된 12자리 면허번호
     */
    public static String normalizeLicenseNo(String licenseNo) {
        if (licenseNo == null || licenseNo.isBlank()) {
            return licenseNo;
        }

        // 공백 제거
        String normalized = licenseNo.trim();

        // 지역명이 포함된 경우 변환
        for (Map.Entry<String, String> entry : REGION_CODE_MAP.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                normalized = entry.getValue() + normalized.substring(entry.getKey().length());
                break;
            }
        }

        // 하이픈(-) 제거
        normalized = normalized.replace("-", "");

        return normalized;
    }

    /**
     * 면허번호가 유효한 12자리인지 검증
     *
     * @param licenseNo 면허번호
     * @return 12자리 숫자면 true
     */
    public static boolean isValidLicenseNo(String licenseNo) {
        if (licenseNo == null) {
            return false;
        }
        return licenseNo.matches("\\d{12}");
    }
}
