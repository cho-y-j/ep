package com.skep.workconfirmation;

public enum WorkConfirmationStatus {
    PENDING,       // 생성됨, 사인 대기
    COMPLETED,     // 양쪽 사인 완료
    CANCELLED,     // 사용자가 취소
    INVALIDATED    // 내용 수정으로 사인 무효화 (재사인 필요)
}
