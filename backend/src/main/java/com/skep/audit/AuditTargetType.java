package com.skep.audit;

public final class AuditTargetType {
    private AuditTargetType() {}

    public static final String SITE = "SITE";
    public static final String SITE_PARTICIPANT = "SITE_PARTICIPANT";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String PERSON = "PERSON";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String DOCUMENT_REVIEW = "DOCUMENT_REVIEW";
    public static final String WORK_PLAN = "WORK_PLAN";
    public static final String WORK_PLAN_EQUIPMENT = "WORK_PLAN_EQUIPMENT";
    public static final String WORK_PLAN_PERSON = "WORK_PLAN_PERSON";

    /** S-10: 견적 요청. */
    public static final String QUOTATION_REQUEST = "QUOTATION_REQUEST";

    /** P0.5a: 계약 / 기통과 소급. */
    public static final String CONTRACT = "CONTRACT";
    public static final String RESOURCE_ONBOARDING = "RESOURCE_ONBOARDING";

    /** P0.5b: 일일 작업확인서. */
    public static final String DAILY_WORK_LOG = "DAILY_WORK_LOG";

    /** P1c: L2a 업체변경 신청서 v0. */
    public static final String RESOURCE_CHANGE_REQUEST = "RESOURCE_CHANGE_REQUEST";
}
