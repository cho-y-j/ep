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
import com.skep.equipment.EquipmentDocRequirementService;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonDocRequirementService;
import com.skep.person.PersonRepository;
import com.skep.person.PersonRole;
import com.skep.security.AuthenticatedUser;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 서류 수집 링크 — 생성/조회/공개 업로드/PDF 합쳐 이메일 발송.
 * V118: request(1) → target(N, 장비+인력 혼합) → item(M). 요청 1건 = 토큰/링크 1개.
 * 수집된 서류는 병합하지 않고 항목별 개별 Document 로 등록(만료·교체·재검증이 서류 단위).
 */
@Service
@Transactional
public class DocumentCollectionService {

    private final DocumentCollectionRequestRepository reqRepo;
    private final DocumentCollectionTargetRepository targetRepo;
    private final DocumentCollectionItemRepository itemRepo;
    private final DocumentTypeRepository typeRepo;
    private final DocumentRepository docRepo;
    private final DocumentService documentService;
    private final FileStorage storage;
    private final PdfMergeService pdfMerge;
    private final CollectionMailService mail;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final EquipmentDocRequirementService equipmentDocReq;
    private final PersonDocRequirementService personDocReq;
    private final com.skep.company.CompanyService companyService;
    private final com.skep.alimtalk.AlimTalkService alimTalk;

    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    public DocumentCollectionService(DocumentCollectionRequestRepository reqRepo, DocumentCollectionTargetRepository targetRepo,
                                     DocumentCollectionItemRepository itemRepo,
                                     DocumentTypeRepository typeRepo, DocumentRepository docRepo, DocumentService documentService,
                                     FileStorage storage, PdfMergeService pdfMerge, CollectionMailService mail,
                                     EquipmentRepository equipmentRepo, PersonRepository personRepo,
                                     EquipmentDocRequirementService equipmentDocReq, PersonDocRequirementService personDocReq,
                                     com.skep.company.CompanyService companyService,
                                     com.skep.alimtalk.AlimTalkService alimTalk) {
        this.reqRepo = reqRepo;
        this.targetRepo = targetRepo;
        this.itemRepo = itemRepo;
        this.typeRepo = typeRepo;
        this.docRepo = docRepo;
        this.documentService = documentService;
        this.storage = storage;
        this.pdfMerge = pdfMerge;
        this.mail = mail;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.equipmentDocReq = equipmentDocReq;
        this.personDocReq = personDocReq;
        this.companyService = companyService;
        this.alimTalk = alimTalk;
    }

    /** 대상 참조(장비/인원) — 생성/추천 요청 검증 공용 키. */
    private record OwnerRef(OwnerType type, Long id) {}

    // ── 작성자(인증) ────────────────────────────────────────

    public CollectionDtos.Response create(CollectionDtos.CreateRequest req, AuthenticatedUser actor) {
        if (actor.role() == Role.WORKER) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        List<CollectionDtos.CreateTarget> targets = req.targets() == null ? List.of() : req.targets();
        if (targets.isEmpty()) throw ApiException.badRequest("TARGETS_REQUIRED", "대상(장비/인원)을 1개 이상 지정하세요");

        List<OwnerRef> refs = new ArrayList<>();
        Set<OwnerRef> seen = new HashSet<>();
        for (CollectionDtos.CreateTarget t : targets) {
            if (t.ownerType() == null || t.ownerId() == null) {
                throw ApiException.badRequest("OWNER_REQUIRED", "대상(장비/인원)을 지정하세요");
            }
            OwnerRef ref = new OwnerRef(t.ownerType(), t.ownerId());
            if (!seen.add(ref)) {
                throw ApiException.badRequest("DUPLICATE_TARGET", ownerLabel(ref) + " 가 중복 지정되었습니다");
            }
            if (nullToEmpty(t.requiredTypeIds()).isEmpty() && nullToEmpty(t.optionalTypeIds()).isEmpty()) {
                throw ApiException.badRequest("NO_TYPES", ownerLabel(ref) + " 의 수집할 서류를 1개 이상 선택하세요");
            }
            refs.add(ref);
        }
        ensureOwnsResources(refs, actor);

        DocumentCollectionRequest r = DocumentCollectionRequest.builder()
                .token(genToken())
                .tokenExpiresAt(LocalDateTime.now().plusDays(14))
                .supplierCompanyId(actor.companyId())
                .createdBy(actor.id())
                .title(req.title())
                .recipientName(req.recipientName())
                .recipientPhone(req.recipientPhone())
                .recipientEmail(req.recipientEmail())
                .build();
        reqRepo.save(r);

        // 선택된 서류타입을 한 번에 로드 (대상 N개 × 타입 M개 findById N+1 방지).
        List<Long> typeIds = targets.stream()
                .flatMap(t -> java.util.stream.Stream.concat(nullToEmpty(t.requiredTypeIds()).stream(), nullToEmpty(t.optionalTypeIds()).stream()))
                .distinct().toList();
        Map<Long, DocumentType> typeById = new HashMap<>();
        for (DocumentType t : typeRepo.findAllById(typeIds)) typeById.put(t.getId(), t);

        // 배열 순서 = sort_order. 대상마다 필수 → 선택 순, 각 타입의 sort_order 로 정렬 보존.
        for (int i = 0; i < targets.size(); i++) {
            CollectionDtos.CreateTarget t = targets.get(i);
            DocumentCollectionTarget tg = targetRepo.save(DocumentCollectionTarget.builder()
                    .requestId(r.getId()).ownerType(t.ownerType()).ownerId(t.ownerId()).sortOrder(i).build());
            saveItems(typeById, tg, nullToEmpty(t.requiredTypeIds()), true);
            saveItems(typeById, tg, nullToEmpty(t.optionalTypeIds()), false);
        }
        return toResponse(r);
    }

    /**
     * 대상 자원의 유형에 설정된 서류를 필수/선택으로 나눠 반환 — 수집요청 폼 자동 체크용.
     * 장비=장비종류(category)별 설정, 인원=역할(roles)별 설정(다중역할이면 합집합·필수 OR).
     * 유형에 설정이 없는 서류는 어느 목록에도 넣지 않는다(= 폼에서 '제외').
     */
    @Transactional(readOnly = true)
    public CollectionDtos.SuggestResponse suggest(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        if (actor.role() == Role.WORKER) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        ensureOwnsResources(List.of(new OwnerRef(ownerType, ownerId)), actor);
        Map<Long, Boolean> requiredByTypeId;
        if (ownerType == OwnerType.EQUIPMENT) {
            Equipment e = equipmentRepo.findById(ownerId)
                    .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "장비를 찾을 수 없습니다"));
            requiredByTypeId = equipmentDocReq.requiredByDocTypeId(e.getCategory());
        } else {
            Person p = personRepo.findById(ownerId)
                    .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "인원을 찾을 수 없습니다"));
            requiredByTypeId = personDocReq.requiredByDocTypeId(p.getRoles());
        }
        return split(requiredByTypeId, typeRepo.findByAppliesToAndActiveOrderBySortOrderAscIdAsc(ownerType, true));
    }

    /** suggest 의 다중 대상판 — document_types 는 owner 유형당 1회, 권한 검사도 1회로 묶는다. */
    @Transactional(readOnly = true)
    public CollectionDtos.SuggestBatchResponse suggestBatch(CollectionDtos.SuggestBatchRequest req, AuthenticatedUser actor) {
        if (actor.role() == Role.WORKER) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        List<CollectionDtos.SuggestTarget> targets = req == null || req.targets() == null ? List.of() : req.targets();
        if (targets.isEmpty()) throw ApiException.badRequest("TARGETS_REQUIRED", "대상(장비/인원)을 1개 이상 지정하세요");
        List<OwnerRef> refs = new ArrayList<>();
        for (CollectionDtos.SuggestTarget t : targets) {
            if (t.ownerType() == null || t.ownerId() == null) {
                throw ApiException.badRequest("OWNER_REQUIRED", "대상(장비/인원)을 지정하세요");
            }
            refs.add(new OwnerRef(t.ownerType(), t.ownerId()));
        }
        ensureOwnsResources(refs, actor);

        Map<OwnerType, List<DocumentType>> activeTypes = new EnumMap<>(OwnerType.class);
        for (OwnerType ot : refs.stream().map(OwnerRef::type).distinct().toList()) {
            activeTypes.put(ot, typeRepo.findByAppliesToAndActiveOrderBySortOrderAscIdAsc(ot, true));
        }
        Map<Long, Equipment> equipment = equipmentById(refs);
        Map<Long, Person> persons = personById(refs);
        // 같은 종류/역할이 반복돼도 요구사항 조회는 1회씩.
        Map<String, Map<Long, Boolean>> byCategory = new HashMap<>();
        Map<Set<PersonRole>, Map<Long, Boolean>> byRoles = new HashMap<>();

        List<CollectionDtos.SuggestBatchResult> results = new ArrayList<>();
        for (OwnerRef ref : refs) {
            Map<Long, Boolean> requiredByTypeId;
            if (ref.type() == OwnerType.EQUIPMENT) {
                Equipment e = equipment.get(ref.id());
                if (e == null) throw ApiException.notFound("NOT_FOUND", "장비를 찾을 수 없습니다: #" + ref.id());
                requiredByTypeId = byCategory.computeIfAbsent(e.getCategory(), equipmentDocReq::requiredByDocTypeId);
            } else {
                Person p = persons.get(ref.id());
                if (p == null) throw ApiException.notFound("NOT_FOUND", "인원을 찾을 수 없습니다: #" + ref.id());
                requiredByTypeId = byRoles.computeIfAbsent(p.getRoles(), personDocReq::requiredByDocTypeId);
            }
            CollectionDtos.SuggestResponse s = split(requiredByTypeId, activeTypes.get(ref.type()));
            results.add(new CollectionDtos.SuggestBatchResult(ref.type(), ref.id(), s.requiredTypeIds(), s.optionalTypeIds()));
        }
        return new CollectionDtos.SuggestBatchResponse(results);
    }

    /** 유형 설정(typeId→required)을 활성 서류 sort_order 순으로 필수/선택 분리. 설정 없는 서류는 제외. */
    private static CollectionDtos.SuggestResponse split(Map<Long, Boolean> requiredByTypeId, List<DocumentType> activeTypes) {
        List<Long> required = new ArrayList<>();
        List<Long> optional = new ArrayList<>();
        for (DocumentType t : activeTypes) {
            Boolean req = requiredByTypeId.get(t.getId());
            if (req == null) continue;
            (req ? required : optional).add(t.getId());
        }
        return new CollectionDtos.SuggestResponse(required, optional);
    }

    private void saveItems(Map<Long, DocumentType> typeById, DocumentCollectionTarget target, List<Long> typeIds, boolean required) {
        for (Long typeId : typeIds) {
            DocumentType t = typeById.get(typeId);
            if (t == null) continue;
            if (t.getAppliesTo() != target.getOwnerType()) {
                throw ApiException.badRequest("TYPE_OWNER_MISMATCH", t.getName() + " 는 " + target.getOwnerType() + " 서류가 아닙니다");
            }
            itemRepo.save(DocumentCollectionItem.builder()
                    .requestId(target.getRequestId()).targetId(target.getId()).documentTypeId(typeId)
                    .required(required).sortOrder(t.getSortOrder()).build());
        }
    }

    @Transactional(readOnly = true)
    public List<CollectionDtos.SummaryResponse> list(AuthenticatedUser actor) {
        List<DocumentCollectionRequest> rows = actor.role() == Role.ADMIN
                ? reqRepo.findAll().stream().sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList()
                : reqRepo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        return toSummaries(rows);
    }

    /** 특정 자원이 대상으로 포함된 요청 목록 — V118 부터 target 기준 조회. */
    @Transactional(readOnly = true)
    public List<CollectionDtos.SummaryResponse> listByOwner(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        List<Long> reqIds = targetRepo.findByOwnerTypeAndOwnerId(ownerType, ownerId).stream()
                .map(DocumentCollectionTarget::getRequestId).distinct().toList();
        if (reqIds.isEmpty()) return List.of();
        List<DocumentCollectionRequest> rows = reqRepo.findAllById(reqIds).stream()
                .filter(r -> canRead(actor, r))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList();
        return toSummaries(rows);
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
        String ownerLabel = ownerSummary(targetRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId()));
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
        List<DocumentCollectionTarget> targets = targetRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        List<DocumentCollectionItem> items = itemRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        Map<Long, DocumentType> types = typesOf(items);
        Map<Long, String> fileNames = fileNamesOf(items);
        Map<Long, String> labels = labelsByTargetId(targets);
        Map<Long, List<DocumentCollectionItem>> byTarget = groupByTarget(items);

        List<CollectionDtos.PublicTarget> pts = targets.stream().map(tg -> {
            List<DocumentCollectionItem> own = byTarget.getOrDefault(tg.getId(), List.of());
            List<CollectionDtos.PublicItem> pis = own.stream().map(it -> {
                DocumentType t = types.get(it.getDocumentTypeId());
                return new CollectionDtos.PublicItem(it.getId(), it.getDocumentTypeId(),
                        t != null ? t.getName() : "(삭제됨)", it.isRequired(), it.getUploadedDocumentId() != null,
                        fileNames.get(it.getUploadedDocumentId()),
                        t != null ? com.skep.document.DocumentTypeService.sampleImageUrl(t) : null,
                        t != null ? t.getSampleDescription() : null);
            }).toList();
            return new CollectionDtos.PublicTarget(tg.getId(), tg.getOwnerType(), labels.get(tg.getId()),
                    own.size(), uploadedCount(own), requiredRemaining(own), pis);
        }).toList();

        return new CollectionDtos.PublicResponse(r.getTitle(), r.getRecipientName(), r.getStatus(), r.isExpired(),
                items.size(), uploadedCount(items), requiredRemaining(items), pts);
    }

    /** 항목(item) 단위 업로드 — 대상별로 같은 서류종류가 여러 건일 수 있어 documentTypeId 로는 특정 불가. */
    public void publicUpload(String token, Long itemId, MultipartFile file) {
        DocumentCollectionRequest r = requireToken(token);
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        DocumentCollectionItem item = itemRepo.findById(itemId)
                .filter(it -> it.getRequestId().equals(r.getId()))
                .orElseThrow(() -> ApiException.badRequest("ITEM_NOT_IN_REQUEST", "요청에 없는 서류입니다"));
        DocumentCollectionTarget target = targetRepo.findById(item.getTargetId())
                .orElseThrow(() -> ApiException.badRequest("ITEM_NOT_IN_REQUEST", "요청에 없는 서류입니다"));
        Document doc = documentService.uploadViaCollection(target.getOwnerType(), target.getOwnerId(),
                item.getDocumentTypeId(), null, file);
        item.attachDocument(doc.getId());
    }

    public void publicSubmit(String token) {
        DocumentCollectionRequest r = requireToken(token);
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        r.markSubmitted();
    }

    // ── helpers ──────────────────────────────────────────────

    private static <T> List<T> nullToEmpty(List<T> v) { return v == null ? List.of() : v; }

    private DocumentCollectionRequest requireToken(String token) {
        DocumentCollectionRequest r = reqRepo.findByToken(token)
                .orElseThrow(() -> ApiException.notFound("INVALID_TOKEN", "유효하지 않은 링크입니다"));
        if ("CANCELLED".equals(r.getStatus())) throw ApiException.badRequest("CANCELLED", "취소된 요청입니다");
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        return r;
    }

    /** 공개(무로그인) 모서리 자동검출용 — 토큰 유효성만 검증(만료/취소 포함). */
    @Transactional(readOnly = true)
    public void assertTokenValid(String token) {
        requireToken(token);
    }

    /** 대상들이 actor 회사 소유 자원인지 검증 — selfAndChildren·자원 조회 모두 배치 1회. ADMIN 예외. */
    private void ensureOwnsResources(List<OwnerRef> refs, AuthenticatedUser actor) {
        for (OwnerRef ref : refs) {
            if (ref.type() != OwnerType.EQUIPMENT && ref.type() != OwnerType.PERSON) {
                throw ApiException.badRequest("BAD_OWNER_TYPE", "지원하지 않는 대상 유형입니다: " + ref.type());
            }
        }
        if (actor.role() == Role.ADMIN) return;
        // V77: 본인 + 직속 자식(부모→자식 단방향) 자원까지 허용. 생성 명의(supplierCompanyId)는 부모 유지.
        List<Long> allowed = companyService.selfAndChildren(actor.companyId());
        Map<Long, Equipment> equipment = equipmentById(refs);
        Map<Long, Person> persons = personById(refs);
        for (OwnerRef ref : refs) {
            Long supplierId = ref.type() == OwnerType.EQUIPMENT
                    ? java.util.Optional.ofNullable(equipment.get(ref.id())).map(Equipment::getSupplierId).orElse(null)
                    : java.util.Optional.ofNullable(persons.get(ref.id())).map(Person::getSupplierId).orElse(null);
            if (supplierId == null || actor.companyId() == null || !allowed.contains(supplierId)) {
                throw ApiException.forbidden("FORBIDDEN", ownerLabel(ref) + " 는 본인/하위 공급사 자원이 아닙니다");
            }
        }
    }

    private Map<Long, Equipment> equipmentById(List<OwnerRef> refs) {
        List<Long> ids = refs.stream().filter(x -> x.type() == OwnerType.EQUIPMENT).map(OwnerRef::id).distinct().toList();
        Map<Long, Equipment> m = new HashMap<>();
        for (Equipment e : equipmentRepo.findAllById(ids)) m.put(e.getId(), e);
        return m;
    }

    private Map<Long, Person> personById(List<OwnerRef> refs) {
        List<Long> ids = refs.stream().filter(x -> x.type() == OwnerType.PERSON).map(OwnerRef::id).distinct().toList();
        Map<Long, Person> m = new HashMap<>();
        for (Person p : personRepo.findAllById(ids)) m.put(p.getId(), p);
        return m;
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

    /** 단건 라벨 — 검증 실패 메시지용(정상 경로는 labelsByTargetId 배치 조회). */
    private String ownerLabel(OwnerRef ref) {
        if (ref.type() == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(ref.id()).map(DocumentCollectionService::equipmentLabel).orElse("장비 #" + ref.id());
        }
        if (ref.type() == OwnerType.PERSON) {
            return personRepo.findById(ref.id()).map(Person::getName).orElse("인원 #" + ref.id());
        }
        return ref.type() + " #" + ref.id();
    }

    private static String equipmentLabel(Equipment e) {
        if (e.getVehicleNo() != null) return e.getVehicleNo();
        return e.getModel() != null ? e.getModel() : "장비 #" + e.getId();
    }

    /** targetId → 표시명. 장비/인원 배치 조회로 목록·상세 N+1 제거. */
    private Map<Long, String> labelsByTargetId(List<DocumentCollectionTarget> targets) {
        List<OwnerRef> refs = targets.stream().map(t -> new OwnerRef(t.getOwnerType(), t.getOwnerId())).toList();
        Map<Long, Equipment> equipment = equipmentById(refs);
        Map<Long, Person> persons = personById(refs);
        Map<Long, String> out = new HashMap<>();
        for (DocumentCollectionTarget t : targets) {
            String label;
            if (t.getOwnerType() == OwnerType.EQUIPMENT) {
                Equipment e = equipment.get(t.getOwnerId());
                label = e != null ? equipmentLabel(e) : "장비 #" + t.getOwnerId();
            } else {
                Person p = persons.get(t.getOwnerId());
                label = p != null ? p.getName() : "인원 #" + t.getOwnerId();
            }
            out.put(t.getId(), label);
        }
        return out;
    }

    /** "12가3456 외 4건" — 목록/메일 제목용 대상 요약. */
    private String ownerSummary(List<DocumentCollectionTarget> targets) {
        return ownerSummary(targets, labelsByTargetId(targets));
    }

    private static String ownerSummary(List<DocumentCollectionTarget> targets, Map<Long, String> labels) {
        if (targets.isEmpty()) return "(대상 없음)";
        String first = labels.get(targets.get(0).getId());
        return targets.size() == 1 ? first : first + " 외 " + (targets.size() - 1) + "건";
    }

    private Map<Long, DocumentType> typesOf(List<DocumentCollectionItem> items) {
        List<Long> ids = items.stream().map(DocumentCollectionItem::getDocumentTypeId).distinct().toList();
        Map<Long, DocumentType> m = new HashMap<>();
        for (DocumentType t : typeRepo.findAllById(ids)) m.put(t.getId(), t);
        return m;
    }

    private Map<Long, String> fileNamesOf(List<DocumentCollectionItem> items) {
        List<Long> ids = items.stream().map(DocumentCollectionItem::getUploadedDocumentId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> m = new HashMap<>();
        for (Document d : docRepo.findAllById(ids)) m.put(d.getId(), d.getFileName());
        return m;
    }

    /** sort_order 정렬된 items 를 target 별로 분배 — 그룹 내 순서 보존. */
    private static Map<Long, List<DocumentCollectionItem>> groupByTarget(List<DocumentCollectionItem> items) {
        return items.stream().collect(Collectors.groupingBy(DocumentCollectionItem::getTargetId));
    }

    private static int uploadedCount(List<DocumentCollectionItem> items) {
        return (int) items.stream().filter(i -> i.getUploadedDocumentId() != null).count();
    }

    private static int requiredRemaining(List<DocumentCollectionItem> items) {
        return (int) items.stream().filter(i -> i.isRequired() && i.getUploadedDocumentId() == null).count();
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
        List<DocumentCollectionTarget> targets = targetRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        List<DocumentCollectionItem> items = itemRepo.findByRequestIdOrderBySortOrderAscIdAsc(r.getId());
        Map<Long, DocumentType> types = typesOf(items);
        Map<Long, String> fileNames = fileNamesOf(items);
        Map<Long, String> labels = labelsByTargetId(targets);
        Map<Long, List<DocumentCollectionItem>> byTarget = groupByTarget(items);

        List<CollectionDtos.TargetResponse> trs = targets.stream().map(tg -> {
            List<DocumentCollectionItem> own = byTarget.getOrDefault(tg.getId(), List.of());
            List<CollectionDtos.ItemResponse> irs = own.stream().map(it -> {
                DocumentType t = types.get(it.getDocumentTypeId());
                return new CollectionDtos.ItemResponse(it.getId(), it.getDocumentTypeId(),
                        t != null ? t.getName() : "(삭제됨)", it.isRequired(), it.getSortOrder(),
                        it.getUploadedDocumentId() != null, it.getUploadedDocumentId(),
                        fileNames.get(it.getUploadedDocumentId()));
            }).toList();
            return new CollectionDtos.TargetResponse(tg.getId(), tg.getOwnerType(), tg.getOwnerId(),
                    labels.get(tg.getId()), tg.getSortOrder(),
                    own.size(), uploadedCount(own), requiredRemaining(own), irs);
        }).toList();

        return new CollectionDtos.Response(r.getId(), r.getToken(), r.getTitle(),
                ownerSummary(targets, labels),
                r.getRecipientName(), r.getRecipientPhone(), r.getRecipientEmail(),
                r.getStatus(), r.getCreatedAt(), r.getSubmittedAt(), r.getSentAt(),
                publicBaseUrl + "/collect/" + r.getToken(),
                targets.size(), items.size(), uploadedCount(items), trs);
    }

    /** 목록용 — 요청 N건의 target/item 을 각각 1쿼리로 모아 카운트만 집계. */
    private List<CollectionDtos.SummaryResponse> toSummaries(List<DocumentCollectionRequest> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(DocumentCollectionRequest::getId).toList();
        List<DocumentCollectionTarget> targets = targetRepo.findByRequestIdIn(ids).stream()
                .sorted(Comparator.comparingInt(DocumentCollectionTarget::getSortOrder)
                        .thenComparing(DocumentCollectionTarget::getId))
                .toList();
        List<DocumentCollectionItem> items = itemRepo.findByRequestIdInOrderBySortOrderAscIdAsc(ids);
        Map<Long, String> labels = labelsByTargetId(targets);
        Map<Long, List<DocumentCollectionTarget>> tByReq = targets.stream()
                .collect(Collectors.groupingBy(DocumentCollectionTarget::getRequestId));
        Map<Long, List<DocumentCollectionItem>> iByReq = items.stream()
                .collect(Collectors.groupingBy(DocumentCollectionItem::getRequestId));

        return rows.stream().map(r -> {
            List<DocumentCollectionTarget> ts = tByReq.getOrDefault(r.getId(), List.of());
            List<DocumentCollectionItem> is = iByReq.getOrDefault(r.getId(), List.of());
            return new CollectionDtos.SummaryResponse(r.getId(), r.getToken(), r.getTitle(),
                    ownerSummary(ts, labels),
                    r.getRecipientName(), r.getRecipientPhone(), r.getRecipientEmail(),
                    r.getStatus(), r.getCreatedAt(), r.getSubmittedAt(), r.getSentAt(),
                    publicBaseUrl + "/collect/" + r.getToken(),
                    ts.size(), is.size(), uploadedCount(is));
        }).toList();
    }
}
