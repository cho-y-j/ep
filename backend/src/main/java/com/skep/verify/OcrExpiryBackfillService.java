package com.skep.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * V82: 서류 만료일 로컬 OCR 비동기 백필 서비스.
 *
 * 대상: verify_endpoint 없는 만료+OCR 타입(=정기검사증). 검증(면허/화물/KOSHA/사업자)은
 * Google Vision 즉시경로(AutoVerifyTrigger)가 처리하므로 여기 오지 않는다 (게이트는 Trigger 에서).
 *
 * 엔진 라우팅:
 *   - paddle: {@link PaddleOcrClient} fullText → {@link OcrExpiryParser} 앵커 파싱 (기본).
 *   - google: {@link VerifyClient#extractOcr} 응답의 top-level expiryDate 재사용 (대체 엔진).
 *
 * 검출 시 expiry_date IS NULL 인 경우에만 write(오탐이 기존값/수동입력을 못 덮음) + audit + 소유사 알림.
 * 미검출/파일읽기 실패 시 아무 것도 안 채운다(graceful).
 */
@Service
@Transactional
public class OcrExpiryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(OcrExpiryBackfillService.class);

    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final FileStorage storage;
    private final PaddleOcrClient paddleClient;
    private final VerifyClient verifyClient;
    private final AuditLogService auditLog;
    private final NotificationService notifications;

    @Value("${ocr.engine:${OCR_ENGINE:off}}")
    private String engine;

    public OcrExpiryBackfillService(DocumentRepository docRepo, DocumentTypeRepository typeRepo,
                                    EquipmentRepository equipmentRepo, PersonRepository personRepo,
                                    FileStorage storage, PaddleOcrClient paddleClient,
                                    VerifyClient verifyClient, AuditLogService auditLog,
                                    NotificationService notifications) {
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.storage = storage;
        this.paddleClient = paddleClient;
        this.verifyClient = verifyClient;
        this.auditLog = auditLog;
        this.notifications = notifications;
    }

    /** 게이트 통과 문서에 대해 OCR 만료일 추출 후 백필. 미검출 시 no-op. */
    public void backfill(Long documentId, AuthenticatedUser actor) {
        Document doc = docRepo.findById(documentId).orElse(null);
        if (doc == null || doc.getExpiryDate() != null) return;
        DocumentType type = typeRepo.findById(doc.getDocumentTypeId()).orElse(null);
        if (type == null) return;

        LocalDate expiry = extractExpiry(doc, type);
        if (expiry == null) {
            log.info("ocr expiry backfill: 만료일 미검출 docId={} type={} engine={}", documentId, type.getName(), engine);
            return;
        }

        int updated = docRepo.updateExpiryIfNull(documentId, expiry, LocalDateTime.now());
        if (updated == 0) return; // 그 사이 다른 경로가 채움 — 덮지 않음.

        Long ownerSupplierId = resolveSupplierId(doc);
        auditLog.record(actor, AuditAction.DOCUMENT_RENEWED, AuditTargetType.DOCUMENT,
                documentId, ownerSupplierId, null,
                "{\"expiry_date\":null}",
                "{\"expiry_date\":\"" + expiry + "\",\"source\":\"OCR_BACKFILL\"}");
        if (ownerSupplierId != null) {
            notifications.sendToCompany(ownerSupplierId, NotificationType.DOCUMENT_EXPIRY_EXTRACTED,
                    type.getName() + " 만료일 자동 입력됨",
                    type.getName() + " 의 만료일이 OCR 로 자동 입력되었습니다: " + expiry,
                    "DOCUMENT", documentId, null, "시스템 (OCR 자동추출)");
        }
        log.info("ocr expiry backfill done docId={} type={} expiry={} engine={}",
                documentId, type.getName(), expiry, engine);
    }

    /** 엔진별 만료일 추출. paddle=로컬 fullText 파싱, google=verify-api expiryDate 재사용. */
    private LocalDate extractExpiry(Document doc, DocumentType type) {
        byte[] bytes = readFileBytes(doc);
        if (bytes == null) return null;
        if ("google".equalsIgnoreCase(engine)) {
            JsonNode ocr = verifyClient.extractOcr(type.getOcrExtractType(), bytes, doc.getFileName());
            if (ocr == null) return null;
            JsonNode e = ocr.get("expiryDate");
            if (e == null || !e.isTextual() || e.asText().isBlank()) return null;
            return parseDate(e.asText());
        }
        // 영역맵 템플릿에 expiry_date 필드가 있으면 영역-크롭 OCR 로 직접 취득(수 초). 파싱은 paddle parser 담당.
        String template = type.getOcrRegionTemplate();
        if (template != null && !template.isBlank() && template.contains("expiry_date")) {
            Map<String, String> fields = paddleClient.extractRegions(bytes, doc.getFileName(), null, template);
            if (fields != null) {
                String ed = fields.get("expiry_date");
                if (ed != null && !ed.isBlank()) {
                    LocalDate d = parseDate(ed);
                    if (d != null) return d;
                }
            }
        }
        // paddle (기본): 로컬 PaddleOCR fullText → 앵커 정규식 파싱.
        String fullText = paddleClient.ocrFullText(bytes, doc.getFileName());
        return OcrExpiryParser.parse(type.getOcrExtractType(), fullText).orElse(null);
    }

    /** ISO(yyyy-MM-dd) 우선, 점/슬래시 구분자면 하이픈으로 정규화 후 재시도. 실패 시 null.
     *  VerificationService 의 verify/OCR 만료일 수확에서도 재사용(패키지 공유). */
    static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(s.trim().replace('.', '-').replace('/', '-'));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private byte[] readFileBytes(Document doc) {
        try {
            Resource res = storage.load(doc.getFileKey());
            try (InputStream is = res.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("ocr backfill 파일 읽기 실패 docId={} key={}: {}", doc.getId(), doc.getFileKey(), e.getMessage());
            return null;
        }
    }

    /** 소유 자원의 공급사 회사 id (알림/audit 대상). */
    private Long resolveSupplierId(Document doc) {
        return switch (doc.getOwnerType()) {
            case EQUIPMENT -> equipmentRepo.findById(doc.getOwnerId()).map(Equipment::getSupplierId).orElse(null);
            case PERSON -> personRepo.findById(doc.getOwnerId()).map(Person::getSupplierId).orElse(null);
            case COMPANY -> doc.getOwnerId();
        };
    }
}
