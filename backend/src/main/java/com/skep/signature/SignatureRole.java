package com.skep.signature;

/** 작업계획서 첫 페이지 5개 사인란. */
public enum SignatureRole {
    AUTHOR,      // 작성자 (Biz.P 현장소장) — BP 본인 사인
    SUPERVISOR,  // 담당자 (SKEP 관리감독자) — 이메일 요청
    CONFIRMER,   // 확인자 (SKEP HYPER) — 이메일 요청
    REVIEWER,    // 검토자 (SKEP 안전관리자) — 이메일 요청
    APPROVER     // 승인자 (SKEP 현장총괄) — 이메일 요청
}
