package com.skep.audit;

public final class AuditTargetType {
    private AuditTargetType() {}

    public static final String SITE = "SITE";
    public static final String SITE_PARTICIPANT = "SITE_PARTICIPANT";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String PERSON = "PERSON";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String WORK_PLAN = "WORK_PLAN";
    public static final String WORK_PLAN_EQUIPMENT = "WORK_PLAN_EQUIPMENT";
    public static final String WORK_PLAN_PERSON = "WORK_PLAN_PERSON";

    /** S-10: 견적 요청. */
    public static final String QUOTATION_REQUEST = "QUOTATION_REQUEST";
}
