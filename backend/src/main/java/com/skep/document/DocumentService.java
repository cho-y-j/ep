package com.skep.document;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.verify.DocumentUploadedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.skep.document.dto.DocumentResponse;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class DocumentService {

    /** 서류로 허용하는 content-type allowlist. inline 렌더링 가능한 HTML/SVG 같은 위험 타입을 차단. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp",
            "image/heic", "image/heif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    /** 미리보기(브라우저 inline 렌더링) 허용 타입. 나머지는 attachment 로 강제. */
    private static final Set<String> INLINE_PREVIEW_TYPES = Set.of(
            "application/pdf",
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"
    );

    public static boolean isInlinePreviewType(String contentType) {
        if (contentType == null) return false;
        return INLINE_PREVIEW_TYPES.contains(contentType.toLowerCase());
    }

    /** 파일 앞 16바이트로 magic-byte 검증. spoofed content-type 차단.
     *  HEIC/HEIF 는 ftyp(offset 4-7) + brand(offset 8-11) 까지 봐야 해서 16 bytes 까지 읽는다. */
    private static boolean matchesMagicBytes(MultipartFile file, String declaredCt) {
        try {
            byte[] head = new byte[16];
            try (var in = file.getInputStream()) {
                int n = in.read(head);
                if (n < 4) return false;
            }
            int b0 = head[0] & 0xFF, b1 = head[1] & 0xFF, b2 = head[2] & 0xFF, b3 = head[3] & 0xFF;
            // PDF: %PDF
            if (declaredCt.equals("application/pdf")) return b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46;
            // PNG
            if (declaredCt.equals("image/png")) return b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47;
            // JPEG
            if (declaredCt.equals("image/jpeg") || declaredCt.equals("image/jpg")) return b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF;
            // GIF
            if (declaredCt.equals("image/gif")) return b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38;
            // WEBP — RIFF(0-3) + WEBP(8-11)
            if (declaredCt.equals("image/webp")) return b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46
                    && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50;
            // HEIC/HEIF — ISO BMFF: bytes 4-7 = "ftyp", bytes 8-11 = brand
            if (declaredCt.equals("image/heic") || declaredCt.equals("image/heif")) {
                if (head[4] != 0x66 || head[5] != 0x74 || head[6] != 0x79 || head[7] != 0x70) return false;
                String brand = new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).toLowerCase();
                return brand.equals("heic") || brand.equals("heix") || brand.equals("heim")
                        || brand.equals("heis") || brand.equals("mif1") || brand.equals("msf1");
            }
            // DOC (MS Compound File): D0 CF 11 E0
            if (declaredCt.equals("application/msword") || declaredCt.equals("application/vnd.ms-excel")) {
                return b0 == 0xD0 && b1 == 0xCF && b2 == 0x11 && b3 == 0xE0;
            }
            // DOCX / XLSX (ZIP): PK
            if (declaredCt.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || declaredCt.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                return b0 == 0x50 && b1 == 0x4B;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private final DocumentRepository docRepo;
    private final DocumentTypeRepository typeRepo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companyRepo;
    private final com.skep.company.CompanyService companyService;
    private final FileStorage storage;
    private final AuditLogService auditLog;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final ApplicationEventPublisher events;
    /** S-11: 보완 요청 자동 RESOLVED 후크. @Lazy 로 순환 회피. */
    private final com.skep.supplement.DocumentSupplementService supplementService;
    private final com.skep.quotation.dispatch.ResourceRenewalNotifier renewalNotifier;
    private final PiiImageMasker piiImageMasker;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public DocumentService(DocumentRepository docRepo, DocumentTypeRepository typeRepo,
                           EquipmentRepository equipmentRepo, PersonRepository personRepo,
                           CompanyRepository companyRepo,
                           com.skep.company.CompanyService companyService,
                           FileStorage storage, AuditLogService auditLog,
                           SiteRepository sites, SiteParticipantRepository participants,
                           ApplicationEventPublisher events,
                           @org.springframework.context.annotation.Lazy
                           com.skep.supplement.DocumentSupplementService supplementService,
                           @org.springframework.context.annotation.Lazy
                           com.skep.quotation.dispatch.ResourceRenewalNotifier renewalNotifier,
                           PiiImageMasker piiImageMasker) {
        this.docRepo = docRepo;
        this.typeRepo = typeRepo;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.companyRepo = companyRepo;
        this.companyService = companyService;
        this.storage = storage;
        this.auditLog = auditLog;
        this.sites = sites;
        this.participants = participants;
        this.events = events;
        this.supplementService = supplementService;
        this.renewalNotifier = renewalNotifier;
        this.piiImageMasker = piiImageMasker;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForOwner(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        Long ownerSupplierId = ownerSupplierIdOrThrow(ownerType, ownerId);
        ensureCanAccess(actor, ownerSupplierId);

        // V14 재등록 체인: chain head (가장 최신 갱신본) 만 노출. 옛 버전은 history endpoint 에서.
        List<Document> docs = docRepo.findActiveHeadByOwner(ownerType, ownerId);
        Map<Long, DocumentType> typeCache = new HashMap<>();
        return docs.stream().map(d -> {
            DocumentType type = typeCache.computeIfAbsent(d.getDocumentTypeId(),
                    id -> typeRepo.findById(id).orElse(null));
            String name = type != null ? type.getName() : "(삭제됨)";
            boolean hasExpiry = type != null && type.isHasExpiry();
            return DocumentResponse.from(d, name, hasExpiry);
        }).toList();
    }

    public DocumentResponse upload(OwnerType ownerType, Long ownerId, Long documentTypeId,
                                   LocalDate expiryDate, MultipartFile file, AuthenticatedUser actor) {
        return upload(ownerType, ownerId, documentTypeId, expiryDate, file, java.util.Map.of(), actor);
    }

    /** S-9-G.2: 사용자가 OCR preview 단계에서 검토/수정한 manual 필드를 extracted_data 에 같이 저장. */
    public DocumentResponse upload(OwnerType ownerType, Long ownerId, Long documentTypeId,
                                   LocalDate expiryDate, MultipartFile file,
                                   java.util.Map<String, String> manualFields, AuthenticatedUser actor) {
        Long ownerSupplierId = ownerSupplierIdOrThrow(ownerType, ownerId);
        ensureCanModify(actor, ownerSupplierId);

        DocumentType type = typeRepo.findById(documentTypeId).orElseThrow(() ->
                ApiException.badRequest("DOCUMENT_TYPE_NOT_FOUND", "document type " + documentTypeId + " not found"));
        if (type.getAppliesTo() != ownerType) {
            throw ApiException.badRequest("DOCUMENT_TYPE_OWNER_MISMATCH",
                    type.getName() + " 는 " + ownerType + " 서류가 아닙니다");
        }
        if (type.isHasExpiry() && expiryDate == null) {
            throw ApiException.badRequest("EXPIRY_REQUIRED", type.getName() + " 는 만료일 입력 필수입니다");
        }

        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE",
                    "지원하지 않는 파일 형식입니다 (PDF / 이미지 / Word / Excel 만 가능)");
        }
        // Content-Type spoof 차단: 파일 헤더 시그니처가 선언한 type 과 일치해야 한다.
        if (!matchesMagicBytes(file, ct)) {
            throw ApiException.badRequest("FILE_HEADER_MISMATCH",
                    "파일 내용이 선언된 형식과 일치하지 않습니다");
        }

        // 주민번호 표기 서류(운전면허증 등)는 마스킹본을 저장 — 원본 PII 를 디스크에 남기지 않는다.
        String storedName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String storedCt = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        long storedSize = file.getSize();
        String key;
        PiiImageMasker.MaskedFile masked = piiImageMasker.maskIfNeeded(type.getName(), file);
        if (masked != null) {
            key = storage.storeBytes(masked.bytes(), ".jpg");
            storedName = masked.fileName();
            storedCt = masked.contentType();
            storedSize = masked.bytes().length;
        } else {
            key = storage.store(file);
        }
        // V14 재등록 체인: 같은 (owner_type, owner_id, type_id) 의 가장 최신 문서가 있으면 그 id 를 previous_document_id 로 묶는다.
        // 옛 문서는 삭제하지 않고 보존 (감사/추적성). list 는 chain head 만 보여준다.
        Long previousDocumentId = docRepo
                .findTopByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc(ownerType, ownerId, documentTypeId)
                .map(Document::getId)
                .orElse(null);

        Document doc = Document.builder()
                .documentTypeId(documentTypeId)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .fileKey(key)
                .fileName(storedName)
                .fileSize(storedSize)
                .contentType(storedCt)
                .expiryDate(expiryDate)
                .uploadedBy(actor.id())
                .previousDocumentId(previousDocumentId)
                .build();
        // S-9-G.2: manual 필드를 extracted_data 에 manual_* 키 prefix 로 저장 (OCR 결과와 병합 시 우선).
        // 제어문자(\n,\t 등) 포함 값도 유효 JSON 이 되도록 ObjectMapper 로 직렬화 (수기 조립은 파싱 실패 위험).
        if (manualFields != null && !manualFields.isEmpty()) {
            try {
                doc.setExtractedData(objectMapper.writeValueAsString(manualFields));
            } catch (Exception ignored) {
                /* serialization 실패 시 manual 필드 무시 */
            }
        }
        docRepo.save(doc);
        auditLog.record(actor, AuditAction.DOCUMENT_UPLOADED, AuditTargetType.DOCUMENT,
                doc.getId(), ownerSupplierId, null,
                null,
                "{\"owner_type\":\"" + ownerType.name() + "\",\"owner_id\":" + ownerId
                        + ",\"document_type\":\"" + type.getName().replace("\"", "\\\"") + "\"}");
        // S-4 단계 3: AFTER_COMMIT 비동기 자동 검증 트리거.
        // verify_endpoint 가 있는 type 에 대해 OCR + 정부 API 호출. 실패해도 사용자 흐름에 영향 없음.
        events.publishEvent(new DocumentUploadedEvent(doc.getId(), actor));
        // S-11: 같은 자원/타입의 OPEN 보완 요청 자동 RESOLVED.
        int resolved = 0;
        try {
            if (supplementService != null) {
                resolved = supplementService.onDocumentUploaded(ownerType, ownerId, documentTypeId, doc.getId());
            }
        } catch (Exception ignored) { /* 보완 요청 처리 실패가 업로드 흐름을 막지 않도록 */ }
        // 보완요청 없이 자발적으로 기존 서류를 갱신(previousDocumentId 존재)한 경우, 서류 묶음 받은 BP 에 갱신 알림.
        // 보완요청이 처리됐으면(resolved>0) 그쪽 알림이 이미 가므로 중복 발송 안 함.
        if (resolved == 0 && previousDocumentId != null && renewalNotifier != null) {
            try {
                renewalNotifier.notifyRenewal(ownerType, ownerId, type.getName(), ownerSupplierId);
            } catch (Exception ignored) { /* 알림 실패가 업로드 흐름을 막지 않도록 */ }
        }
        return DocumentResponse.from(doc, type.getName(), type.isHasExpiry());
    }

    /**
     * 서류 수집 공개 링크 업로드 — 토큰이 owner 를 인가하므로 actor 스코핑 없이 저장.
     * 내부 검증(타입/소유 일치, content-type allowlist, 매직바이트, PII 마스킹)은 그대로 재사용. 감사/이벤트는 생략.
     */
    public Document uploadViaCollection(OwnerType ownerType, Long ownerId, Long documentTypeId,
                                        LocalDate expiryDate, MultipartFile file) {
        DocumentType type = typeRepo.findById(documentTypeId).orElseThrow(() ->
                ApiException.badRequest("DOCUMENT_TYPE_NOT_FOUND", "document type " + documentTypeId + " not found"));
        if (type.getAppliesTo() != ownerType) {
            throw ApiException.badRequest("DOCUMENT_TYPE_OWNER_MISMATCH", type.getName() + " 는 " + ownerType + " 서류가 아닙니다");
        }
        if (type.isHasExpiry() && expiryDate == null) {
            throw ApiException.badRequest("EXPIRY_REQUIRED", type.getName() + " 는 만료일 입력 필수입니다");
        }
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "지원하지 않는 파일 형식입니다 (PDF / 이미지 / Word / Excel 만 가능)");
        }
        if (!matchesMagicBytes(file, ct)) {
            throw ApiException.badRequest("FILE_HEADER_MISMATCH", "파일 내용이 선언된 형식과 일치하지 않습니다");
        }
        String storedName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String storedCt = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        long storedSize = file.getSize();
        String key;
        PiiImageMasker.MaskedFile masked = piiImageMasker.maskIfNeeded(type.getName(), file);
        if (masked != null) {
            key = storage.storeBytes(masked.bytes(), ".jpg");
            storedName = masked.fileName();
            storedCt = masked.contentType();
            storedSize = masked.bytes().length;
        } else {
            key = storage.store(file);
        }
        Long previousDocumentId = docRepo
                .findTopByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc(ownerType, ownerId, documentTypeId)
                .map(Document::getId).orElse(null);
        Document doc = Document.builder()
                .documentTypeId(documentTypeId).ownerType(ownerType).ownerId(ownerId)
                .fileKey(key).fileName(storedName).fileSize(storedSize).contentType(storedCt)
                .expiryDate(expiryDate).uploadedBy(null).previousDocumentId(previousDocumentId)
                .build();
        docRepo.save(doc);
        try {
            if (supplementService != null) supplementService.onDocumentUploaded(ownerType, ownerId, documentTypeId, doc.getId());
        } catch (Exception ignored) { /* 보완요청 처리 실패가 업로드를 막지 않도록 */ }
        return doc;
    }

    /**
     * ADMIN 검토 큐. verification_status 가 OCR_REVIEW_REQUIRED 또는 REJECTED 인 chain head.
     * 응답에 owner/supplier 메타까지 합본하여 검토 페이지가 한 번에 사용.
     */
    @Transactional(readOnly = true)
    public List<com.skep.document.dto.ReviewItemResponse> reviewQueue(AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("REVIEW_ADMIN_ONLY", "검토 큐는 ADMIN 만 조회 가능합니다");
        }
        List<Document> rows = docRepo.findReviewQueue();
        if (rows.isEmpty()) return List.of();

        Map<Long, DocumentType> typeMap = new HashMap<>();
        for (Document d : rows) typeMap.computeIfAbsent(d.getDocumentTypeId(), id -> typeRepo.findById(id).orElse(null));

        return rows.stream().map(d -> {
            DocumentType t = typeMap.get(d.getDocumentTypeId());
            String typeName = t != null ? t.getName() : "(삭제됨)";
            String ownerName;
            Long supplierId;
            if (d.getOwnerType() == OwnerType.EQUIPMENT) {
                Equipment e = equipmentRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = e != null ? (e.getModel() != null ? e.getModel()
                        : (e.getVehicleNo() != null ? e.getVehicleNo() : ("장비 #" + d.getOwnerId())))
                        : "(삭제됨)";
                supplierId = e != null ? e.getSupplierId() : null;
            } else if (d.getOwnerType() == OwnerType.COMPANY) {
                // S-9-G: 회사 서류 — owner 자체가 회사.
                Company c = companyRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = c != null ? c.getName() : "(삭제됨)";
                supplierId = d.getOwnerId();
            } else {
                Person p = personRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = p != null ? p.getName() : "(삭제됨)";
                supplierId = p != null ? p.getSupplierId() : null;
            }
            String supplierName = supplierId != null
                    ? companyRepo.findById(supplierId).map(Company::getName).orElse("회사 #" + supplierId)
                    : null;
            return new com.skep.document.dto.ReviewItemResponse(
                    d.getId(), d.getDocumentTypeId(), typeName,
                    d.getOwnerType(), d.getOwnerId(), ownerName, null, null,
                    false, null,
                    supplierId, supplierName,
                    d.getFileName(), d.getExpiryDate(),
                    d.getVerificationStatus(), d.getRejectedReason(),
                    // ADMIN 검토 큐 — 원본 노출. 마스킹된 값으로 재검증 시 RIMS/NTS 가 실패함.
                    d.getVerificationResult(),
                    d.getExtractedData(),
                    d.getCreatedAt()
            );
        }).toList();
    }

    /**
     * ADMIN 이 처리 완료한 큐 — VERIFIED 또는 REJECTED 인 chain head 중 verifiedAt 이 찍힌 것.
     */
    @Transactional(readOnly = true)
    public List<com.skep.document.dto.ReviewItemResponse> processedQueue(AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("REVIEW_ADMIN_ONLY", "처리 완료 큐는 ADMIN 만 조회 가능합니다");
        }
        List<Document> rows = docRepo.findProcessedQueue();
        if (rows.isEmpty()) return List.of();

        Map<Long, DocumentType> typeMap = new HashMap<>();
        for (Document d : rows) typeMap.computeIfAbsent(d.getDocumentTypeId(), id -> typeRepo.findById(id).orElse(null));

        return rows.stream().map(d -> {
            DocumentType t = typeMap.get(d.getDocumentTypeId());
            String typeName = t != null ? t.getName() : "(삭제됨)";
            String ownerName;
            Long supplierId;
            if (d.getOwnerType() == OwnerType.EQUIPMENT) {
                Equipment e = equipmentRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = e != null ? (e.getModel() != null ? e.getModel()
                        : (e.getVehicleNo() != null ? e.getVehicleNo() : ("장비 #" + d.getOwnerId())))
                        : "(삭제됨)";
                supplierId = e != null ? e.getSupplierId() : null;
            } else if (d.getOwnerType() == OwnerType.COMPANY) {
                Company c = companyRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = c != null ? c.getName() : "(삭제됨)";
                supplierId = d.getOwnerId();
            } else {
                Person p = personRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = p != null ? p.getName() : "(삭제됨)";
                supplierId = p != null ? p.getSupplierId() : null;
            }
            String supplierName = supplierId != null
                    ? companyRepo.findById(supplierId).map(Company::getName).orElse("회사 #" + supplierId)
                    : null;
            return new com.skep.document.dto.ReviewItemResponse(
                    d.getId(), d.getDocumentTypeId(), typeName,
                    d.getOwnerType(), d.getOwnerId(), ownerName, null, null,
                    false, null,
                    supplierId, supplierName,
                    d.getFileName(), d.getExpiryDate(),
                    d.getVerificationStatus(), d.getRejectedReason(),
                    // ADMIN 검토 큐 — 원본 노출. 마스킹된 값으로 재검증 시 RIMS/NTS 가 실패함.
                    d.getVerificationResult(),
                    d.getExtractedData(),
                    d.getCreatedAt()
            );
        }).toList();
    }

    /**
     * ADMIN 만료 임박 큐. 30일(또는 days 파라미터) 이내 만료 예정 서류.
     * reviewQueue 와 동일한 ReviewItemResponse 응답 사용.
     */
    @Transactional(readOnly = true)
    public List<com.skep.document.dto.ReviewItemResponse> expiringQueue(AuthenticatedUser actor, int days) {
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("REVIEW_ADMIN_ONLY", "만료 임박 큐는 ADMIN 만 조회 가능합니다");
        }
        if (days < 0) days = 30;
        LocalDate maxDate = LocalDate.now().plusDays(days);
        List<Document> rows = docRepo.findExpiringAll(maxDate);
        if (rows.isEmpty()) return List.of();

        Map<Long, DocumentType> typeMap = new HashMap<>();
        for (Document d : rows) typeMap.computeIfAbsent(d.getDocumentTypeId(), id -> typeRepo.findById(id).orElse(null));

        return rows.stream().map(d -> {
            DocumentType t = typeMap.get(d.getDocumentTypeId());
            String typeName = t != null ? t.getName() : "(삭제됨)";
            String ownerName;
            Long supplierId;
            if (d.getOwnerType() == OwnerType.EQUIPMENT) {
                Equipment e = equipmentRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = e != null ? (e.getModel() != null ? e.getModel()
                        : (e.getVehicleNo() != null ? e.getVehicleNo() : ("장비 #" + d.getOwnerId())))
                        : "(삭제됨)";
                supplierId = e != null ? e.getSupplierId() : null;
            } else if (d.getOwnerType() == OwnerType.COMPANY) {
                Company c = companyRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = c != null ? c.getName() : "(삭제됨)";
                supplierId = d.getOwnerId();
            } else {
                Person p = personRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = p != null ? p.getName() : "(삭제됨)";
                supplierId = p != null ? p.getSupplierId() : null;
            }
            String supplierName = supplierId != null
                    ? companyRepo.findById(supplierId).map(Company::getName).orElse("회사 #" + supplierId)
                    : null;
            return new com.skep.document.dto.ReviewItemResponse(
                    d.getId(), d.getDocumentTypeId(), typeName,
                    d.getOwnerType(), d.getOwnerId(), ownerName, null, null,
                    false, null,
                    supplierId, supplierName,
                    d.getFileName(), d.getExpiryDate(),
                    d.getVerificationStatus(), d.getRejectedReason(),
                    // ADMIN 검토 큐 — 원본 노출. 마스킹된 값으로 재검증 시 RIMS/NTS 가 실패함.
                    d.getVerificationResult(),
                    d.getExtractedData(),
                    d.getCreatedAt()
            );
        }).toList();
    }

    /** 공급사 본인 회사 자원 documents — 만료 관리 페이지용. */
    @Transactional(readOnly = true)
    public List<com.skep.document.dto.ReviewItemResponse> mySupplierDocuments(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 조회 가능");
        }
        Long companyId = actor.companyId();
        if (companyId == null) return List.of();
        List<Document> rows = docRepo.findMySupplierDocuments(companyId);
        if (rows.isEmpty()) return List.of();
        Map<Long, DocumentType> typeMap = new HashMap<>();
        for (Document d : rows) typeMap.computeIfAbsent(d.getDocumentTypeId(), id -> typeRepo.findById(id).orElse(null));
        return rows.stream().map(d -> {
            DocumentType t = typeMap.get(d.getDocumentTypeId());
            String typeName = t != null ? t.getName() : "(삭제됨)";
            String ownerName;
            String ownerSubLabel = null;
            String ownerAssignmentStatus = null;
            boolean ownerExternal = false;
            String ownerBusinessName = null;
            if (d.getOwnerType() == OwnerType.EQUIPMENT) {
                Equipment e = equipmentRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = e != null ? (e.getVehicleNo() != null ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel() : ("장비 #" + d.getOwnerId()))) : "(삭제됨)";
                if (e != null && e.getCategory() != null) ownerSubLabel = e.getCategory().name();
                if (e != null && e.getAssignmentStatus() != null) ownerAssignmentStatus = e.getAssignmentStatus().name();
                if (e != null) { ownerExternal = e.isExternal(); ownerBusinessName = e.getVehicleOwnerName(); }
            } else if (d.getOwnerType() == OwnerType.COMPANY) {
                Company c = companyRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = c != null ? c.getName() : "(삭제됨)";
            } else {
                Person p = personRepo.findById(d.getOwnerId()).orElse(null);
                ownerName = p != null ? p.getName() : "(삭제됨)";
                if (p != null && p.getRoles() != null && !p.getRoles().isEmpty()) {
                    ownerSubLabel = p.getRoles().stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(null);
                }
                if (p != null && p.getAssignmentStatus() != null) ownerAssignmentStatus = p.getAssignmentStatus().name();
            }
            return new com.skep.document.dto.ReviewItemResponse(
                    d.getId(), d.getDocumentTypeId(), typeName,
                    d.getOwnerType(), d.getOwnerId(), ownerName, ownerSubLabel, ownerAssignmentStatus,
                    ownerExternal, ownerBusinessName,
                    companyId, null,
                    d.getFileName(), d.getExpiryDate(),
                    d.getVerificationStatus(), d.getRejectedReason(),
                    null, null,
                    d.getCreatedAt()
            );
        }).toList();
    }

    /**
     * 갱신 이력: 주어진 doc 의 같은 (owner_type, owner_id, document_type_id) 모든 버전.
     * 권한은 owner read 권한과 동일.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> history(Long documentId, AuthenticatedUser actor) {
        Document head = docRepo.findById(documentId).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + documentId + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(head.getOwnerType(), head.getOwnerId());
        ensureCanAccess(actor, supplierId);

        DocumentType type = typeRepo.findById(head.getDocumentTypeId()).orElse(null);
        String name = type != null ? type.getName() : "(삭제됨)";
        boolean hasExpiry = type != null && type.isHasExpiry();

        return docRepo.findByOwnerTypeAndOwnerIdAndDocumentTypeIdOrderByIdDesc(
                        head.getOwnerType(), head.getOwnerId(), head.getDocumentTypeId())
                .stream()
                .map(d -> DocumentResponse.from(d, name, hasExpiry))
                .toList();
    }

    @Transactional(readOnly = true)
    public Document getForDownload(Long documentId, AuthenticatedUser actor) {
        Document d = docRepo.findById(documentId).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + documentId + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        ensureCanAccess(actor, supplierId);
        return d;
    }

    public Resource loadFile(Document d) {
        return storage.load(d.getFileKey());
    }

    public DocumentResponse updateExpiry(Long id, LocalDate expiryDate, AuthenticatedUser actor) {
        Document d = docRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + id + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        ensureCanModify(actor, supplierId);
        LocalDate before = d.getExpiryDate();
        d.updateExpiry(expiryDate);
        DocumentType type = typeRepo.findById(d.getDocumentTypeId()).orElseThrow();
        // 만료일 갱신 = 서류 갱신(DOCUMENT_RENEWED). 별도 renew endpoint 가 따로 있으면 합치기.
        auditLog.record(actor, AuditAction.DOCUMENT_RENEWED, AuditTargetType.DOCUMENT,
                d.getId(), supplierId, null,
                "{\"expiry_date\":\"" + (before != null ? before : "") + "\"}",
                "{\"expiry_date\":\"" + (expiryDate != null ? expiryDate : "") + "\"}");
        return DocumentResponse.from(d, type.getName(), type.isHasExpiry());
    }

    public DocumentResponse setVerified(Long id, boolean verified, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("VERIFY_ADMIN_ONLY", "검증 표시는 관리자만 가능합니다");
        }
        Document d = docRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + id + " not found"));
        boolean before = d.isVerified();
        // V14 정책: verified_by / verified_at 도 함께 채운다.
        if (verified) d.markVerifiedBy(actor.id()); else d.unmarkVerified();
        DocumentType type = typeRepo.findById(d.getDocumentTypeId()).orElseThrow();
        Long ownerSupplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        auditLog.record(actor, AuditAction.DOCUMENT_VERIFIED, AuditTargetType.DOCUMENT,
                d.getId(), ownerSupplierId, null,
                "{\"verified\":" + before + "}",
                "{\"verified\":" + verified + ",\"by\":" + actor.id() + "}");
        return DocumentResponse.from(d, type.getName(), type.isHasExpiry());
    }

    public void delete(Long id, AuthenticatedUser actor) {
        Document d = docRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("DOCUMENT_NOT_FOUND", "document " + id + " not found"));
        Long supplierId = ownerSupplierIdOrThrow(d.getOwnerType(), d.getOwnerId());
        ensureCanModify(actor, supplierId);
        String key = d.getFileKey();
        docRepo.delete(d);
        // 파일은 마지막에 삭제. 트랜잭션 롤백 시 DB row 는 살아있고 파일만 사라지는 상황 방지엔 좀 부족하지만 실용적.
        storage.delete(key);
    }

    private Long ownerSupplierIdOrThrow(OwnerType ownerType, Long ownerId) {
        if (ownerType == OwnerType.EQUIPMENT) {
            Equipment e = equipmentRepo.findById(ownerId).orElseThrow(() ->
                    ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + ownerId + " not found"));
            return e.getSupplierId();
        }
        if (ownerType == OwnerType.COMPANY) {
            // S-9-G: 회사 자체가 서류 owner. supplierId == ownerId (자기 회사 서류).
            // 회사 존재 확인은 권한 분기 단계에서 충분 — 여기선 ownerId 그대로 반환.
            return ownerId;
        }
        Person p = personRepo.findById(ownerId).orElseThrow(() ->
                ApiException.notFound("PERSON_NOT_FOUND", "person " + ownerId + " not found"));
        return p.getSupplierId();
    }

    private void ensureCanAccess(AuthenticatedUser actor, Long ownerSupplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            // V77: 읽기만 본인 + 직속 자식 확장(부모→자식 단방향). 쓰기(ensureCanModify)는 본인 회사 고정 유지.
            if (!companyService.selfAndChildren(actor.companyId()).contains(ownerSupplierId)) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사의 서류만 접근 가능합니다");
            }
            return;
        }
        if (actor.role() == Role.BP) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
            }
            // S-9-G: BP 본인 회사 서류 (COMPANY ownerType) 는 항상 read 가능.
            if (ownerSupplierId.equals(actor.companyId())) return;
            // 그 외 — 자기 BP 사이트의 ACTIVE 참여 공급사 자원 서류만 read.
            List<Long> mySiteIds = sites.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream()
                    .map(s -> s.getId())
                    .toList();
            for (Long siteId : mySiteIds) {
                if (participants.existsBySiteIdAndCompanyIdAndStatus(siteId, ownerSupplierId, SiteParticipantStatus.ACTIVE)) {
                    return;
                }
            }
            throw ApiException.forbidden("DOCUMENT_ACCESS_DENIED",
                    "내 현장에 참여 중인 공급사의 서류만 확인할 수 있습니다");
        }
        // WORKER 등은 서류 접근 불가 (Phase S-3 정책).
        throw ApiException.forbidden("DOCUMENT_ACCESS_DENIED", "서류 접근 권한이 없습니다");
    }

    private void ensureCanModify(AuthenticatedUser actor, Long ownerSupplierId) {
        if (actor.role() == Role.ADMIN) return;
        // S-9-G: 모든 회사 사용자 (BP/EQUIPMENT_SUPPLIER/MANPOWER_SUPPLIER) 가 본인 회사 서류 (COMPANY ownerType) 는 업로드/수정 가능.
        // EQUIPMENT_SUPPLIER/MANPOWER_SUPPLIER 는 자기 자원(EQUIPMENT/PERSON) 서류도 가능.
        if ((actor.role() == Role.BP || actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && ownerSupplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }
}
