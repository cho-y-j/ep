package com.skep.verify;

import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * V82: 서류 upload 직후 로컬 OCR 만료일 백필 트리거.
 *
 * upload 트랜잭션 commit 후(AFTER_COMMIT) 전용 스레드(ocrExecutor)에서 비동기 실행 —
 * 로컬 PaddleOCR(~90초)이 HTTP/이벤트 스레드를 점유하지 않게 한다.
 *
 * 게이트: ocr.engine!=off && verify_endpoint 없음 && ocr_enabled && has_expiry && expiry_date==null.
 * verify_endpoint 보유 타입(운전면허/화물/KOSHA/사업자)은 여기서 제외 — Google Vision 즉시경로
 * (AutoVerifyTrigger)가 처리하며, 두 리스너의 게이트는 상호배타적이다.
 */
@Component
public class OcrExpiryBackfillTrigger {

    private static final Logger log = LoggerFactory.getLogger(OcrExpiryBackfillTrigger.class);

    private final OcrExpiryBackfillService backfillService;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;

    @Value("${ocr.engine:${OCR_ENGINE:off}}")
    private String engine;

    public OcrExpiryBackfillTrigger(OcrExpiryBackfillService backfillService,
                                    DocumentRepository docRepo, DocumentTypeRepository typeRepo) {
        this.backfillService = backfillService;
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
    }

    @Async("ocrExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUpload(DocumentUploadedEvent event) {
        try {
            if ("off".equalsIgnoreCase(engine)) return;
            Document doc = docRepo.findById(event.documentId()).orElse(null);
            if (doc == null || doc.getExpiryDate() != null) return;
            DocumentType type = typeRepo.findById(doc.getDocumentTypeId()).orElse(null);
            if (type == null) return;
            // verify_endpoint 보유 = 즉시 Vision 경로 → 백필 제외.
            if (type.getVerifyEndpoint() != null && !type.getVerifyEndpoint().isBlank()) return;
            if (!type.isOcrEnabled() || !type.isHasExpiry()) return;

            backfillService.backfill(event.documentId(), event.actor());
        } catch (Exception e) {
            // 백필 실패는 사용자 흐름에 영향 X. 로그만.
            log.warn("ocr expiry backfill trigger failed docId={}: {}", event.documentId(), e.getMessage());
        }
    }
}
