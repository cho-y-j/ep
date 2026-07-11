package com.skep.quotation.dispatch.draft;

/** DRAFT: 생성됨(미발송). CONFIRMED: confirm 으로 실제 dispatched 생성됨. DISCARDED: 수동 send 로 대체돼 폐기됨. */
public enum DispatchDraftStatus {
    DRAFT, CONFIRMED, DISCARDED
}
