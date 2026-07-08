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

    public static final String EQUIPMENT_STATUS_CHANGED = "EQUIPMENT_STATUS_CHANGED";

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

    // S-10: 견적 요청
    public static final String QUOTATION_CREATED = "QUOTATION_CREATED";
    public static final String QUOTATION_RESPONDED = "QUOTATION_RESPONDED";
    public static final String QUOTATION_FINALIZED = "QUOTATION_FINALIZED";
    public static final String QUOTATION_CANCELLED = "QUOTATION_CANCELLED";
}
