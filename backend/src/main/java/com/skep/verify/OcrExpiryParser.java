package com.skep.verify;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR fullText 에서 만료일(LocalDate)만 파싱.
 *
 * verify-api {@code OcrExtractController} 의 만료일 앵커 정규식을 기반으로 하되,
 * paddle 읽기순서(표 레이아웃)에 맞게 window 를 넓히고 "검사유효기간 = 시작~끝 범위"의
 * 끝 날짜를 캡처하도록 보강했다(verify-api 는 별 모듈이라 import 불가).
 *   - LICENSE:                OcrExtractController.java:406 (parseLicense, 첫 매치)
 *   - CARGO:                  OcrExtractController.java:587 (parseCargo, 첫 매치)
 *   - EQUIPMENT_REGISTRATION: 검사유효기간 범위면 끝 날짜 캡처, 아니면 단일 만료일 폴백.
 *
 * 신뢰도: 앵커 미검출 또는 연도 비정상(2000..now+30) 이면 Optional.empty().
 * expiry_date IS NULL 인 경우에만 write 되므로 오탐이 기존값을 덮지 않는다.
 */
public final class OcrExpiryParser {

    private OcrExpiryParser() {}

    // 운전면허증 만료일/적성검사 만료 — OcrExtractController.java:406
    private static final Pattern LICENSE_EXPIRY = Pattern.compile(
            "(?:적성검사기간|유효기간|갱신기간|갱신기한)[\\s\\S]{0,40}?(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");

    // 화물운송자격증 만료일 — OcrExtractController.java:587
    private static final Pattern CARGO_EXPIRY = Pattern.compile(
            "(?:유효기간|갱신기한|만료일)\\s*[:：]?\\s*(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");

    // 자동차등록증/정기검사증 검사유효기간 — 범위(시작 끝)면 끝 날짜(date2)를 캡처.
    // paddle 읽기순서에선 표 레이아웃상 앵커와 날짜가 멀리(관측 186자) 떨어져 window 를 300 으로 넓힌다.
    // 시작일(date1)은 버리고 끝일(date2)만 캡처 — 검사유효기간은 항상 시작~끝, 구분자는 공백/개행/물결.
    private static final Pattern VEHICLE_EXPIRY_RANGE = Pattern.compile(
            "(?:검사유효기간|정기검사\\s*만료일|보험기간|유효기간)[\\s\\S]{0,300}?"
          + "\\d{4}\\s*[.\\-/년]\\s*\\d{1,2}\\s*[.\\-/월]\\s*\\d{1,2}"        // 시작일(버림)
          + "[\\s~]+"                                                           // 범위 구분(공백/개행/물결)
          + "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");  // 끝일(캡처)

    // 범위가 아니라 단일 만료일만 있는 경우 폴백.
    private static final Pattern VEHICLE_EXPIRY_SINGLE = Pattern.compile(
            "(?:검사유효기간|정기검사\\s*만료일|보험기간|유효기간)[\\s\\S]{0,300}?"
          + "(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");

    /** extractType ∈ {LICENSE, CARGO, EQUIPMENT_REGISTRATION}. 그 외/미검출 → empty. */
    public static Optional<LocalDate> parse(String extractType, String fullText) {
        if (extractType == null || fullText == null || fullText.isBlank()) return Optional.empty();
        return switch (extractType) {
            case "LICENSE" -> firstMatch(LICENSE_EXPIRY, fullText);
            case "CARGO" -> firstMatch(CARGO_EXPIRY, fullText);
            case "EQUIPMENT_REGISTRATION" -> {
                Optional<LocalDate> end = firstMatch(VEHICLE_EXPIRY_RANGE, fullText);
                yield end.isPresent() ? end : firstMatch(VEHICLE_EXPIRY_SINGLE, fullText);
            }
            default -> Optional.empty();
        };
    }

    /** 첫 매치 (앵커 뒤 캡처그룹 3개 = 연/월/일). */
    private static Optional<LocalDate> firstMatch(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (m.find()) return toSaneDate(m.group(1), m.group(2), m.group(3));
        return Optional.empty();
    }

    /** 연도 sane(2000..now+30) + 실제 달력 유효성 확인 후 LocalDate 반환. */
    private static Optional<LocalDate> toSaneDate(String year, String month, String day) {
        try {
            int y = Integer.parseInt(year);
            if (y < 2000 || y > LocalDate.now().getYear() + 30) return Optional.empty();
            return Optional.of(LocalDate.of(y, Integer.parseInt(month), Integer.parseInt(day)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
