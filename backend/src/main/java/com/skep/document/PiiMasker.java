package com.skep.document;

import java.util.regex.Pattern;

/**
 * 한국 PII (개인정보) 마스킹. OCR 결과 / 사용자 manual 입력에 섞인 PII 를
 * 응답으로 노출할 때 토큰화한다. DB 원본은 그대로 유지하며 ADMIN unmask endpoint 로만 조회.
 *
 * 신분증 / 운전면허증 / 자동차등록증 OCR 결과 마스킹 대상:
 * - 주민번호 뒷 6자리 (성별 1자리만 노출) — 신분증, 자동차등록증 차주
 * - 운전면허번호 가운데 6자리 — 운전면허증
 * - 사업자등록번호 가운데 5자리 (XXX-XX-***** → 등록번호 일부) — 자동차등록증 사업자
 * - 차대번호 (VIN) 가운데 11자리 — 자동차등록증
 */
public final class PiiMasker {

    private PiiMasker() {}

    // YYMMDD-G******  (G=성별 1자리, RRN 7자리 중 1자리 + 6자리 마스킹)
    private static final Pattern RRN = Pattern.compile("(\\d{6})[-\\s]?([1-4])\\d{6}");

    // XX-XX-XXXXXX-XX 운전면허번호. 지역(2)-연도(2)-일련(6)-체크(2). 가운데 6자리 마스킹.
    private static final Pattern DRIVER = Pattern.compile("(\\d{2})[-\\s]?(\\d{2})[-\\s]?\\d{6}[-\\s]?(\\d{2})");

    // 사업자등록번호 XXX-XX-XXXXX → XXX-**-***** (가운데 5자리 + 끝 5자리 마스킹).
    // 자동차등록증 사업자 정보 식별 회피용. 단, 일반 사업자등록증 자체는 마스킹 대상 아님 (검증 필요).
    // 끝 5자리는 식별성 높아서 마스킹, 앞 3자리(지역코드)만 노출.
    private static final Pattern BIZ_NO = Pattern.compile("(\\d{3})[-\\s](\\d{2})[-\\s](\\d{5})");

    // 차대번호 (VIN). 17자리 영숫자 [A-HJ-NPR-Z0-9] (I,O,Q 제외). 가운데 11자리 마스킹, 앞 3 + 뒤 3만 노출.
    private static final Pattern VIN = Pattern.compile("\\b([A-HJ-NPR-Z0-9]{3})[A-HJ-NPR-Z0-9]{11}([A-HJ-NPR-Z0-9]{3})\\b");

    /** 문자열 안의 모든 한국 PII 를 마스킹된 형태로 치환. null 안전. */
    public static String mask(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String out = RRN.matcher(raw).replaceAll("$1-$2******");
        out = DRIVER.matcher(out).replaceAll("$1-$2-******-$3");
        out = BIZ_NO.matcher(out).replaceAll("$1-**-*****");
        out = VIN.matcher(out).replaceAll("$1***********$2");
        return out;
    }
}
