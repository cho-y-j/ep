package com.skep.notification;

/** 알림 type 키. 새 type 은 여기에 상수로 추가. */
public final class NotificationType {
    private NotificationType() {}

    public static final String DOCUMENT_REJECTED = "DOCUMENT_REJECTED";
    public static final String DOCUMENT_OCR_REVIEW = "DOCUMENT_OCR_REVIEW";
    public static final String DOCUMENT_VERIFIED = "DOCUMENT_VERIFIED";
    public static final String DOCUMENT_EXPIRING = "DOCUMENT_EXPIRING";
    public static final String DOCUMENT_EXPIRED = "DOCUMENT_EXPIRED";
    /** V82: 로컬 OCR 비동기 백필로 서류 만료일이 자동 추출/입력됨 → 소유사. */
    public static final String DOCUMENT_EXPIRY_EXTRACTED = "DOCUMENT_EXPIRY_EXTRACTED";
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

    /** V77: 하위 공급사(협력사) 자가가입 → 부모 회사 승인 대기. */
    public static final String SUB_SUPPLIER_SIGNUP = "SUB_SUPPLIER_SIGNUP";

    /** B1: 매월 1일 배차행 보유 공급사에게 전월 거래내역서 준비 통지(월마감). */
    public static final String MONTHLY_STATEMENT_READY = "MONTHLY_STATEMENT_READY";

    /** P1c: L2 자원 교체로 새 계획서가 원 계획서를 대체 → 원 계획서 관련 공급사/BP 통지. */
    public static final String WORK_PLAN_RESOURCE_REPLACED = "WORK_PLAN_RESOURCE_REPLACED";

    /** P3a S1: 강풍 작업중지 경보(임계 초과) → 작업자 소속 공급사·BP. */
    public static final String WIND_STOP = "WIND_STOP";
    /** P3a S1: 강풍 해제(임계 이하 복귀) → 작업자 소속 공급사·BP. */
    public static final String WIND_CLEARED = "WIND_CLEARED";
    /** P3a S3: 일일점검 미완 장비로 작업 시작(경고 모드) → BP. */
    public static final String DAILY_INSPECTION_INCOMPLETE = "DAILY_INSPECTION_INCOMPLETE";
    /** P3a S4': 가동시간 누적이 정비 주기 도달 → 장비 공급사. */
    public static final String EQUIPMENT_MAINTENANCE_DUE = "EQUIPMENT_MAINTENANCE_DUE";
    /** P3b S5': 안전알림 5분 미확인 → BP·공급사 관리자("○○○ 미확인"). */
    public static final String SAFETY_ACK_MISSING = "SAFETY_ACK_MISSING";
}
