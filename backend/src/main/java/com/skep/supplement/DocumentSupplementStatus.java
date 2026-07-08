package com.skep.supplement;

public enum DocumentSupplementStatus {
    OPEN,        // 발송됨, 공급사가 갱신 대기
    RESOLVED,    // 공급사가 새 서류 업로드 → 자동 또는 수동 close
    CANCELLED    // 요청자가 취소
}
