package com.skep.collection;

import com.skep.collection.dto.CollectionDtos;
import com.skep.common.ApiException;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentService;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** 서류 수집 링크 — 생성/조회/공개 업로드/PDF 합쳐 이메일 발송. */
@Service
@Transactional
public class DocumentCollectionService {

    private final DocumentCollectionRequestRepository reqRepo;
    private final DocumentCollectionItemRepository itemRepo;
    private final DocumentTypeRepository typeRepo;
    private final DocumentRepository docRepo;
    private final DocumentService documentService;
    private final FileStorage storage;
    private final PdfMergeService pdfMerge;
    private final CollectionMailService mail;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final com.skep.company.CompanyService companyService;
    private final com.skep.alimtalk.AlimTalkService alimTalk;

    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    public DocumentCollectionService(DocumentCollectionRequestRepository reqRepo, DocumentCollectionItemRepository itemRepo,
                                     DocumentTypeRepository typeRepo, DocumentRepository docRepo, DocumentService documentService,
                                     FileStorage storage, PdfMergeService pdfMerge, CollectionMailService mail,
                                     EquipmentRepository equipmentRepo, PersonRepository personRepo,
                                     com.skep.company.CompanyService companyService,
                                     com.skep.alimtalk.AlimTalkService alimTalk) {
        this.reqRepo = reqRepo;
        this.itemRepo = itemRepo;
        this.typeRepo = typeRepo;
        this.docRepo = docRepo;
        this.documentService = documentService;
        this.storage = storage;
        this.pdfMerge = pdfMerge;
        this.mail = mail;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.companyService = companyService;
        this.alimTalk = alimTalk;
    }

    // ── 작성자(인증) ────────────────────────────────────────

    public CollectionDtos.Response create(CollectionDtos.CreateRequest req, AuthenticatedUser actor) {
        if (actor.role() == Role.WORKER) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        if (req.ownerType() == null || req.ownerId() == null) {
            throw ApiException.badRequest("OWNER_REQUIRED", "대상(장비/인원)을 지정하세요");
        }
        ensureOwnsResource(req.ownerType(), req.ownerId(), actor);
        List<Long> required = req.requiredTypeIds() == null ? List.of() : req.requiredTypeIds();
        List<Long> optional = req.optionalTypeIds() == null ? List.of() : req.optionalTypeIds();
        if (required.isEmpty() && optional.isEmpty()) {
            throw ApiException.badRequest("NO_TYPES", "수집할 서류를 1개 이상 선택하세요");
        }
        DocumentCollectionRequest r = DocumentCollectionRequest.builder()
                .token(genToken())
                .tokenExpiresAt(LocalDateTime.now().plusDays(14))
                .ownerType(req.ownerType())
                .ownerId(req.ownerId())
                .supplierCompanyId(actor.companyId())
                .createdBy(actor.id())
                .title(req.title())
                .recipientName(req.recipientName())
                .recipientPhone(req.recipientPhone())
                .recipientEmail(req.recipientEmail())
                .build();
        reqRepo.save(r);
        // 필수 → 선택 순으로, 각 타입의 sort_order 로 정렬 보존.
        saveItems(r.getId(), req.ownerType(), required, true);
        saveItems(r.getId(), req.ownerType(), optional, false);
        return toResponse(r);
    }

    private void saveItems(Long requestId, OwnerType ownerType, List<Long> typeIds, boolean required) {
        for (Long typeId : typeIds) {
            DocumentType t = typeRepo.findById(typeId).orElse(null);
            if (t == null) continue;
            if (t.getAppliesTo() != ownerType) {
                throw ApiException.badRequest("TYPE_OWNER_MISMATCH", t.getName() + " 는 " + ownerType + " 서류가 아닙니다");
            }
            itemRepo.save(DocumentCollectionItem.builder()
                    .requestId(requestId).documentTypeId(typeId)
                    .required(required).sortOrder(t.getSortOrder()).build());
        }
    }

    @Transactional(readOnly = true)
    public List<CollectionDtos.Response> list(AuthenticatedUser actor) {
        List<DocumentCollectionRequest> rows = actor.role() == Role.ADMIN
                ? reqRepo.findAll().stream().sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList()
                : reqRepo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CollectionDtos.Response> listByOwner(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        return reqRepo.findByOwnerTypeAndOwnerIdOrderByIdDesc(ownerType, ownerId).stream()
                .filter(r -> canRead(actor, r)).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CollectionDtos.Response get(Long id, AuthenticatedUser actor) {
        DocumentCollectionRequest r = reqRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "수집 요청을 찾을 수 없습니다"));
        if (!canRead(actor, r)) throw ApiException.forbidden("FORBIDDEN", "조회 권한이 없습니다");
        return toResponse(r);
    }

    public void cancel(Long id, AuthenticatedUser actor) {
        DocumentCollectionRequest r = reqRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "수집 요청을 찾을 수 없습니다"));
        if (!canAccess(actor, r)) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        r.cancel();
    }

    /** 수집된 서류를 순서대로 PDF로 합쳐 이메일 발송. */
    public CollectionDtos.Response compileAndSend(Long id, CollectionDtos.SendPdfRequest req, AuthenticatedUser actor) {
        DocumentCollectionRequest r = reqRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "수집 요청을 찾을 수 없습니다"));
        if (!canAccess(actor, r)) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        String to = req != null && req.email() != null && !req.email().isBlank() ? req.email().trim() : r.getRecipientEmail();
        if (to == null || to.isBlank()) throw ApiException.badRequest("EMAIL_REQUIRED", "받는 이메일을 입력하세요");

        List<DocumentCollectionItem> items = itemRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        List<PdfMergeService.Part> parts = new ArrayList<>();
        for (DocumentCollectionItem it : items) {
            if (it.getUploadedDocumentId() == null) continue;
            Document d = docRepo.findById(it.getUploadedDocumentId()).orElse(null);
            if (d == null) continue;
            byte[] bytes = readBytes(d.getFileKey());
            if (bytes != null) parts.add(new PdfMergeService.Part(bytes, d.getContentType(), d.getFileName()));
        }
        if (parts.isEmpty()) throw ApiException.badRequest("NO_DOCS", "합칠 서류가 없습니다");

        byte[] pdf = pdfMerge.merge(parts);
        String ownerLabel = ownerLabel(r.getOwnerType(), r.getOwnerId());
        String subject = req != null && req.subject() != null && !req.subject().isBlank()
                ? req.subject().trim()
                : "[서류 취합] " + (r.getTitle() != null && !r.getTitle().isBlank() ? r.getTitle() : ownerLabel);
        String body = (r.getRecipientName() != null ? r.getRecipientName() + " 님, " : "")
                + ownerLabel + " 관련 수집 서류를 합친 PDF를 첨부합니다.";
        String filename = sanitize(ownerLabel) + "_취합서류.pdf";
        mail.sendPdf(to, subject, body, pdf, filename);
        r.markSent();
        return toResponse(r);
    }

    /** 공개 링크를 받는사람 휴대폰으로 문자(SMS) 발송 — 다온톡 게이트웨이. */
    public com.skep.alimtalk.AlimTalkService.SendResult sendLink(Long id, AuthenticatedUser actor) {
        DocumentCollectionRequest r = reqRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "수집 요청을 찾을 수 없습니다"));
        if (!canAccess(actor, r)) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        if (r.getRecipientPhone() == null || r.getRecipientPhone().isBlank()) {
            throw ApiException.badRequest("PHONE_REQUIRED", "받는사람 연락처가 없습니다");
        }
        String url = publicBaseUrl + "/collect/" + r.getToken();
        // SMS(단문)는 90바이트 한계 — 링크 URL만 ~60바이트라 문구는 짧게 유지.
        String content = "서류 제출 링크\n" + url;
        return alimTalk.sendSmsText(r.getRecipientPhone(), content, actor.id());
    }

    // ── 공개(무로그인, 토큰) ──────────────────────────────────

    @Transactional(readOnly = true)
    public CollectionDtos.PublicResponse publicGet(String token) {
        DocumentCollectionRequest r = requireToken(token);
        List<DocumentCollectionItem> items = itemRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        List<CollectionDtos.PublicItem> pis = items.stream().map(it -> {
            DocumentType t = typeRepo.findById(it.getDocumentTypeId()).orElse(null);
            String fn = null;
            if (it.getUploadedDocumentId() != null) {
                Document d = docRepo.findById(it.getUploadedDocumentId()).orElse(null);
                fn = d != null ? d.getFileName() : null;
            }
            return new CollectionDtos.PublicItem(it.getDocumentTypeId(),
                    t != null ? t.getName() : "(삭제됨)", it.isRequired(), it.getUploadedDocumentId() != null, fn,
                    t != null ? com.skep.document.DocumentTypeService.sampleImageUrl(t) : null);
        }).collect(Collectors.toList());
        return new CollectionDtos.PublicResponse(r.getTitle(), r.getOwnerType(),
                ownerLabel(r.getOwnerType(), r.getOwnerId()), r.getRecipientName(), r.getStatus(), r.isExpired(), pis);
    }

    public void publicUpload(String token, Long documentTypeId, MultipartFile file) {
        DocumentCollectionRequest r = requireToken(token);
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        DocumentCollectionItem item = itemRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId()).stream()
                .filter(it -> it.getDocumentTypeId().equals(documentTypeId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("ITEM_NOT_IN_REQUEST", "요청에 없는 서류입니다"));
        Document doc = documentService.uploadViaCollection(r.getOwnerType(), r.getOwnerId(), documentTypeId, null, file);
        item.attachDocument(doc.getId());
    }

    public void publicSubmit(String token) {
        DocumentCollectionRequest r = requireToken(token);
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        r.markSubmitted();
    }

    // ── helpers ──────────────────────────────────────────────

    private DocumentCollectionRequest requireToken(String token) {
        DocumentCollectionRequest r = reqRepo.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("INVALID_TOKEN", "유효하지 않은 링크입니다"));
        if ("CANCELLED".equals(r.getStatus())) throw ApiException.badRequest("CANCELLED", "취소된 요청입니다");
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        return r;
    }

    /** create 대상(장비/인원)이 actor 회사 소유 자원인지 검증. ADMIN 예외. */
    private void ensureOwnsResource(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Long supplierId;
        if (ownerType == OwnerType.EQUIPMENT) {
            supplierId = equipmentRepo.findById(ownerId).map(Equipment::getSupplierId).orElse(null);
        } else if (ownerType == OwnerType.PERSON) {
            supplierId = personRepo.findById(ownerId).map(Person::getSupplierId).orElse(null);
        } else {
            throw ApiException.badRequest("BAD_OWNER_TYPE", "지원하지 않는 대상 유형입니다");
        }
        // V77: 본인 + 직속 자식(부모→자식 단방향) 자원까지 허용. 생성 명의(supplierCompanyId)는 부모 유지.
        if (supplierId == null || actor.companyId() == null
                || !companyService.selfAndChildren(actor.companyId()).contains(supplierId)) {
            throw ApiException.forbidden("FORBIDDEN", "본인/하위 공급사 자원만 서류수집을 생성할 수 있습니다");
        }
    }

    /** 쓰기(취소/발송/링크전송) 권한 — 본인 회사 고정. V77 자식 확장 금지. */
    private boolean canAccess(AuthenticatedUser actor, DocumentCollectionRequest r) {
        if (actor.role() == Role.ADMIN) return true;
        return actor.companyId() != null && actor.companyId().equals(r.getSupplierCompanyId());
    }

    /** V77 읽기 전용 권한 — 본인 + 직속 자식(부모→자식 단방향). 자식/형제는 selfAndChildren={본인} 이라 자동 차단. */
    private boolean canRead(AuthenticatedUser actor, DocumentCollectionRequest r) {
        if (actor.role() == Role.ADMIN) return true;
        return actor.companyId() != null
                && companyService.selfAndChildren(actor.companyId()).contains(r.getSupplierCompanyId());
    }

    private String genToken() {
        for (int i = 0; i < 20; i++) {
            String t = UUID.randomUUID().toString().replace("-", "");
            if (!reqRepo.existsByToken(t)) return t;
        }
        throw ApiException.conflict("TOKEN_EXHAUSTED", "토큰 생성에 반복 실패했습니다");
    }

    private String ownerLabel(OwnerType ownerType, Long ownerId) {
        if (ownerType == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(ownerId)
                    .map(e -> e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "장비 #" + ownerId))
                    .orElse("장비 #" + ownerId);
        }
        if (ownerType == OwnerType.PERSON) {
            return personRepo.findById(ownerId).map(Person::getName).orElse("인원 #" + ownerId);
        }
        return ownerType + " #" + ownerId;
    }

    private byte[] readBytes(String key) {
        try {
            return storage.load(key).getInputStream().readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "doc";
        return s.replaceAll("[^0-9A-Za-z가-힣_-]", "_");
    }

    private CollectionDtos.Response toResponse(DocumentCollectionRequest r) {
        List<DocumentCollectionItem> items = itemRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        List<CollectionDtos.ItemResponse> irs = items.stream().map(it -> {
            DocumentType t = typeRepo.findById(it.getDocumentTypeId()).orElse(null);
            String fn = null;
            if (it.getUploadedDocumentId() != null) {
                Document d = docRepo.findById(it.getUploadedDocumentId()).orElse(null);
                fn = d != null ? d.getFileName() : null;
            }
            return new CollectionDtos.ItemResponse(it.getDocumentTypeId(),
                    t != null ? t.getName() : "(삭제됨)", it.isRequired(), it.getSortOrder(),
                    it.getUploadedDocumentId() != null, it.getUploadedDocumentId(), fn);
        }).toList();
        String publicUrl = publicBaseUrl + "/collect/" + r.getToken();
        return new CollectionDtos.Response(r.getId(), r.getToken(), r.getOwnerType(), r.getOwnerId(),
                ownerLabel(r.getOwnerType(), r.getOwnerId()), r.getTitle(),
                r.getRecipientName(), r.getRecipientPhone(), r.getRecipientEmail(),
                r.getStatus(), r.getCreatedAt(), r.getSubmittedAt(), r.getSentAt(), publicUrl, irs);
    }
}
