package com.skep.verify;

import com.skep.security.AuthenticatedUser;

/**
 * 서류가 업로드된 직후 발행되는 이벤트.
 * AFTER_COMMIT 시점에 listener 가 자동 OCR + 검증을 트리거한다.
 *
 * actor 는 업로드한 사용자. listener 가 권한 컨텍스트로 사용한다.
 */
public record DocumentUploadedEvent(Long documentId, AuthenticatedUser actor) {
}
