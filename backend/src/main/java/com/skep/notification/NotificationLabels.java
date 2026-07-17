package com.skep.notification;

import com.skep.person.PersonRole;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestType;

import java.time.LocalDate;

/**
 * 알림 message 에 들어갈 사람 친화 라벨 빌더.
 *
 * 사용자에게 "견적 #29" / "제안 #1" 같은 raw ID 대신 자원 종류 · 기간 · 현장 정보를 노출.
 * Link target id 는 그대로 link_target_id 컬럼에 남으니 라우팅엔 영향 없음.
 */
public final class NotificationLabels {
    private NotificationLabels() {}

    /** 이미 해석된 종류 라벨(또는 code)을 받아 표시. 빈 값이면 "장비". DB 이름 해석은 호출부(EquipmentTypeService)가 담당. */
    public static String equipmentCategory(String label) {
        if (label == null || label.isBlank()) return "장비";
        return label;
    }

    public static String personRole(PersonRole r) {
        if (r == null) return "인력";
        return switch (r) {
            case OPERATOR -> "조종원";
            case WORK_DIRECTOR -> "작업지휘자";
            case GUIDE -> "유도원";
            case FIRE_WATCH -> "화기감시자";
            case SIGNALER -> "신호수";
            case INSPECTOR -> "점검원";
            case SITE_MANAGER -> "소장";
        };
    }

    /** 견적 요청을 짧은 라벨로. 예: "굴삭기 · 7/10~7/15 · 강남현장". 장비 종류 라벨은 호출부가 해석해 전달. */
    public static String quotationLabel(QuotationRequest qr, String siteName, String equipmentCategoryLabel) {
        String resource = qr.getRequestType() == QuotationRequestType.MANPOWER
                ? personRole(qr.getManpowerRole())
                : equipmentCategory(equipmentCategoryLabel);
        String place = siteName != null ? siteName
                : (qr.getWorkLocationText() != null && !qr.getWorkLocationText().isBlank()
                        ? qr.getWorkLocationText() : "장소 협의");
        String period = shortPeriod(qr.getWorkPeriodStart(), qr.getWorkPeriodEnd());
        return resource + " · " + period + " · " + place;
    }

    /** "7/10~7/15" 형태로 축약. 연도가 같으면 월/일만, 다르면 YYYY-MM-DD. null 이면 "기간 미정". */
    public static String shortPeriod(LocalDate s, LocalDate e) {
        if (s == null && e == null) return "기간 미정";
        if (s == null) return "~" + formatShort(e, false);
        if (e == null) return formatShort(s, false) + "~";
        boolean sameYear = s.getYear() == e.getYear();
        return formatShort(s, false) + "~" + formatShort(e, sameYear);
    }

    private static String formatShort(LocalDate d, boolean omitYear) {
        if (d == null) return "";
        if (omitYear) return d.getMonthValue() + "/" + d.getDayOfMonth();
        return d.getMonthValue() + "/" + d.getDayOfMonth();
    }
}
