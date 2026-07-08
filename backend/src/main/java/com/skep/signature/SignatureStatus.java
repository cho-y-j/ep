package com.skep.signature;

public enum SignatureStatus {
    PENDING,       // 토큰 생성됨, 사인 대기 (이메일 발송됨)
    SIGNED,        // 사인 완료
    EXPIRED,       // 토큰 만료 (7일 경과)
    INVALIDATED    // 워크시트 수정으로 무효화됨
}
