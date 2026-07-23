package com.skep.audit;

/**
 * 표준화된 감사 로그 액션 키. 새 액션을 도메인 서비스에서 기록할 때 여기에 상수로 추가한다.
 */
public final class AuditAction {
    private AuditAction() {}

    public static final String SITE_CREATED = "SITE_CREATED";
    public static final String SITE_UPDATED = "SITE_UPDATED";
    public static final String PARTICIPANT_ADDED = "PARTICIPANT_ADDED";
    public static final String PARTICIPANT_REMOVED = "PARTICIPANT_REMOVED";

    public static final String EQUIPMENT_ASSIGNED = "EQUIPMENT_ASSIGNED";
    public static final String EQUIPMENT_UNASSIGNED = "EQUIPMENT_UNASSIGNED";
    public static final String PERSON_ASSIGNED = "PERSON_ASSIGNED";
    public static final String PERSON_UNASSIGNED = "PERSON_UNASSIGNED";

    // S-8.1: 작업계획서 시작 시 자동 동기화에서 다른 사이트에 이미 배치된 자원 발견.
    public static final String WORK_PLAN_RESOURCE_CONFLICT = "WORK_PLAN_RESOURCE_CONFLICT";
    // S-8.5: 다른 사이트 충돌이 있는데도 ADMIN 이 강제로 시작.
    public static final String WORK_PLAN_FORCE_STARTED = "WORK_PLAN_FORCE_STARTED";

    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    public static final String DOCUMENT_RENEWED = "DOCUMENT_RENEWED";
    public static final String DOCUMENT_VERIFIED = "DOCUMENT_VERIFIED";

    // V96: BP 서류 심사 봉투 승인/반려.
    public static final String DOCUMENT_REVIEW_APPROVED = "DOCUMENT_REVIEW_APPROVED";
    public static final String DOCUMENT_REVIEW_REJECTED = "DOCUMENT_REVIEW_REJECTED";

    public static final String EQUIPMENT_STATUS_CHANGED = "EQUIPMENT_STATUS_CHANGED";

    // R1 조합(차량+조종원): 장비 조합(교대조) 조종원 매칭 변경 — 전/후 person_ids 요약.
    public static final String EQUIPMENT_DEFAULT_OPERATORS_CHANGED = "EQUIPMENT_DEFAULT_OPERATORS_CHANGED";

    // Phase S-5: 작업계획서
    public static final String WORK_PLAN_CREATED = "WORK_PLAN_CREATED";
    public static final String WORK_PLAN_CLONED = "WORK_PLAN_CLONED";
    public static final String WORK_PLAN_UPDATED = "WORK_PLAN_UPDATED";
    public static final String WORK_PLAN_SUBMITTED = "WORK_PLAN_SUBMITTED";
    public static final String WORK_PLAN_APPROVED = "WORK_PLAN_APPROVED";
    public static final String WORK_PLAN_STARTED = "WORK_PLAN_STARTED";
    public static final String WORK_PLAN_COMPLETED = "WORK_PLAN_COMPLETED";
    public static final String WORK_PLAN_CANCELLED = "WORK_PLAN_CANCELLED";
    public static final String WORK_PLAN_EQUIPMENT_ADDED = "WORK_PLAN_EQUIPMENT_ADDED";
    public static final String WORK_PLAN_EQUIPMENT_REMOVED = "WORK_PLAN_EQUIPMENT_REMOVED";
    public static final String WORK_PLAN_PERSON_ADDED = "WORK_PLAN_PERSON_ADDED";
    public static final String WORK_PLAN_PERSON_REMOVED = "WORK_PLAN_PERSON_REMOVED";
    // P1a 기반②: 서명 스냅샷 저장 / 내용변경 시 서명 전원 무효화.
    public static final String WORK_PLAN_SIGN_SNAPSHOT = "WORK_PLAN_SIGN_SNAPSHOT";
    public static final String WORK_PLAN_SIGNATURES_INVALIDATED = "WORK_PLAN_SIGNATURES_INVALIDATED";
    // P1c: L2 자원 교체 — 새 계획서 대체 생성 + 원본 자동 종료.
    public static final String WORK_PLAN_RESOURCE_REPLACED = "WORK_PLAN_RESOURCE_REPLACED";
    // P1c: L2a 업체변경 신청서 v0.
    public static final String RESOURCE_CHANGE_REQUEST_CREATED = "RESOURCE_CHANGE_REQUEST_CREATED";

    // S-10: 견적 요청
    public static final String QUOTATION_CREATED = "QUOTATION_CREATED";
    public static final String QUOTATION_RESPONDED = "QUOTATION_RESPONDED";
    public static final String QUOTATION_FINALIZED = "QUOTATION_FINALIZED";
    public static final String QUOTATION_CANCELLED = "QUOTATION_CANCELLED";

    // P0.5a: 계약(단가 원천).
    public static final String CONTRACT_CREATED = "CONTRACT_CREATED";
    public static final String CONTRACT_UPDATED = "CONTRACT_UPDATED";

    // P0.5a: 기통과 소급 + 구두승인.
    public static final String RESOURCE_ONBOARDING_REQUESTED = "RESOURCE_ONBOARDING_REQUESTED";
    public static final String RESOURCE_ONBOARDING_APPROVED = "RESOURCE_ONBOARDING_APPROVED";
    public static final String RESOURCE_ONBOARDING_VERBAL = "RESOURCE_ONBOARDING_VERBAL";

    // P0.5b: 일일 작업확인서.
    public static final String DAILY_WORK_LOG_CREATED = "DAILY_WORK_LOG_CREATED";
    public static final String DAILY_WORK_LOG_UPDATED = "DAILY_WORK_LOG_UPDATED";
    public static final String DAILY_WORK_LOG_SIGNED = "DAILY_WORK_LOG_SIGNED";
}
