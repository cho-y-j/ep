package com.skep.verify;

import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 서류 upload 직후 자동 검증 트리거.
 *
 * - upload 트랜잭션 commit 후 (AFTER_COMMIT) 별도 thread 에서 비동기 실행
 *   → upload 응답을 OCR/외부 API 호출 동안 막지 않음.
 * - DocumentType 의 verify_endpoint 가 있는 경우만 시도. user_inputs 는 비어 있음
 *   (자동 트리거는 OCR 결과만으로 시도, 부족하면 OCR_REVIEW_REQUIRED 로 떨어짐).
 * - VERIFY_ENABLED=false 면 VerifyClient 자체가 UPSTREAM_DISABLED 응답 → 결과적으로 OCR_REVIEW_REQUIRED.
 */
@Component
public class AutoVerifyTrigger {

    private static final Logger log = LoggerFactory.getLogger(AutoVerifyTrigger.class);

    private final VerificationService verificationService;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;

    public AutoVerifyTrigger(VerificationService verificationService,
                             DocumentRepository docRepo, DocumentTypeRepository typeRepo) {
        this.verificationService = verificationService;
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpload(DocumentUploadedEvent event) {
        try {
            Document doc = docRepo.findById(event.documentId()).orElse(null);
            if (doc == null) return;
            DocumentType type = typeRepo.findById(doc.getDocumentTypeId()).orElse(null);
            if (type == null) return;
            // 자동 검증 대상이 아니면 skip — verification_status 는 PENDING 으로 남는다.
            if (type.getVerifyEndpoint() == null || type.getVerifyEndpoint().isBlank()) return;

            verificationService.verifyDocument(event.documentId(), Map.of(), event.actor());
            log.info("auto-verify done docId={} type={}", event.documentId(), type.getName());
        } catch (Exception e) {
            // 자동 트리거 실패는 사용자 흐름에 영향 X. 로그만.
            log.warn("auto-verify failed docId={}: {}", event.documentId(), e.getMessage());
        }
    }
}
