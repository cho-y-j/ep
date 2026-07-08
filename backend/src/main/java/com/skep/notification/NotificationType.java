package com.skep.notification;

/** 알림 type 키. 새 type 은 여기에 상수로 추가. */
public final class NotificationType {
    private NotificationType() {}

    public static final String DOCUMENT_REJECTED = "DOCUMENT_REJECTED";
    public static final String DOCUMENT_OCR_REVIEW = "DOCUMENT_OCR_REVIEW";
    public static final String DOCUMENT_VERIFIED = "DOCUMENT_VERIFIED";
    public static final String DOCUMENT_EXPIRING = "DOCUMENT_EXPIRING";
    public static final String DOCUMENT_EXPIRED = "DOCUMENT_EXPIRED";
    public static final String ASSIGNMENT_OVERRIDDEN = "ASSIGNMENT_OVERRIDDEN";

    /** S-10: 견적 요청 발송 → 공급사. */
    public static final String QUOTATION_RECEIVED = "QUOTATION_RECEIVED";
    /** S-10: 공급사 응답 → BP/ADMIN. */
    public static final String QUOTATION_RESPONDED = "QUOTATION_RESPONDED";
    /** S-10: BP/ADMIN 최종 수락 → 공급사. */
    public static final String QUOTATION_FINALIZED = "QUOTATION_FINALIZED";
    /** V33: 공개입찰 다른 제안 선정/close 로 자동 거절 → 공급사. */
    public static final String QUOTATION_REJECTED = "QUOTATION_REJECTED";

    /** S-11: BP/ADMIN 가 공급사에게 서류 보완 요청. */
    public static final String SUPPLEMENT_REQUESTED = "SUPPLEMENT_REQUESTED";
    /** S-11: 공급사가 새 서류 업로드 → 자동 RESOLVED. */
    public static final String SUPPLEMENT_RESOLVED = "SUPPLEMENT_RESOLVED";

    /** V55: 공급사 → BP 현장 투입 요청. */
    public static final String FIELD_DEPLOYMENT_REQUESTED = "FIELD_DEPLOYMENT_REQUESTED";
    /** V55: BP 의 수락/반려. */
    public static final String FIELD_DEPLOYMENT_REVIEWED = "FIELD_DEPLOYMENT_REVIEWED";

    /** V70: 차량 정기검사/오일교체/등록 만료 임박. */
    public static final String EQUIPMENT_DUE = "EQUIPMENT_DUE";
}
