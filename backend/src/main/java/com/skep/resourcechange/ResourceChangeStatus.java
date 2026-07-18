package com.skep.resourcechange;

/** 업체변경 신청서 상태. v0 는 DRAFT 생성만(서명·확정 CONFIRMED 는 후속). */
public enum ResourceChangeStatus {
    DRAFT,
    CONFIRMED
}
