package com.skep.collection;

import com.skep.collection.dto.CollectionDtos;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
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
import java.util.LinkedHashSet;
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
    private final DocumentUploadMerger uploadMerger;
    private final CollectionMailService mail;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final EquipmentDocRequirementService equipmentDocReq;
    private final PersonDocRequirementService personDocReq;
    private final com.skep.company.CompanyService companyService;
    private final CompanyRepository companyRepo;
    private final com.skep.alimtalk.AlimTalkService alimTalk;
    private final com.skep.equipment.EquipmentService equipmentService;
    private final com.skep.person.PersonService personService;
    private final com.skep.equipment.EquipmentTypeService equipmentTypes;

    @Value("${SKEP_PUBLIC_BASE_URL:http://localhost:8082}")
    private String publicBaseUrl;

    public DocumentCollectionService(DocumentCollectionRequestRepository reqRepo, DocumentCollectionTargetRepository targetRepo,
                                     DocumentCollectionItemRepository itemRepo,
                                     DocumentTypeRepository typeRepo, DocumentRepository docRepo, DocumentService documentService,
                                     FileStorage storage, PdfMergeService pdfMerge, DocumentUploadMerger uploadMerger,
                                     CollectionMailService mail,
                                     EquipmentRepository equipmentRepo, PersonRepository personRepo,
                                     EquipmentDocRequirementService equipmentDocReq, PersonDocRequirementService personDocReq,
                                     com.skep.company.CompanyService companyService,
                                     CompanyRepository companyRepo,
                                     com.skep.alimtalk.AlimTalkService alimTalk,
                                     com.skep.equipment.EquipmentService equipmentService,
                                     com.skep.person.PersonService personService,
                                     com.skep.equipment.EquipmentTypeService equipmentTypes) {
        this.reqRepo = reqRepo;
        this.targetRepo = targetRepo;
        this.itemRepo = itemRepo;
        this.typeRepo = typeRepo;
        this.docRepo = docRepo;
        this.documentService = documentService;
        this.storage = storage;
        this.pdfMerge = pdfMerge;
        this.uploadMerger = uploadMerger;
        this.mail = mail;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.equipmentDocReq = equipmentDocReq;
        this.personDocReq = personDocReq;
        this.companyService = companyService;
        this.companyRepo = companyRepo;
        this.alimTalk = alimTalk;
        this.equipmentService = equipmentService;
        this.personService = personService;
        this.equipmentTypes = equipmentTypes;
    }

    /** 대상 참조(장비/인원) — 생성/추천 요청 검증 공용 키. */
    private record OwnerRef(OwnerType type, Long id) {}

    // ── 작성자(인증) ────────────────────────────────────────

    public CollectionDtos.Response create(CollectionDtos.CreateRequest req, AuthenticatedUser actor) {
        if (actor.role() == Role.WORKER) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        List<CollectionDtos.CreateTarget> targets = req.targets() == null ? List.of() : req.targets();
        if (targets.isEmpty()) throw ApiException.badRequest("TARGETS_REQUIRED", "대상(장비/인원)을 1개 이상 지정하세요");

        // 등록형(ownerId 없음) 감지 — 한 요청에 갱신형+등록형 혼합 금지.
        boolean anyRegister = targets.stream().anyMatch(t -> t.ownerId() == null);
        if (anyRegister) {
            if (targets.stream().anyMatch(t -> t.ownerId() != null)) {
                throw ApiException.badRequest("MIXED_MODE", "갱신형과 신규 등록형을 한 요청에 섞을 수 없습니다");
            }
            return createRegister(req, targets, actor);
        }

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
     * 등록형 요청 생성 — 공개 링크에서 값 입력 시 자원을 만들 미등록 슬롯(owner_id=null) 을 quantity 만큼 생성.
     * 권한: EQUIPMENT_SUPPLIER master 또는 ADMIN. 소유 협력업체(targetCompanyId)는 본인/직속 자식만.
     */
    private CollectionDtos.Response createRegister(CollectionDtos.CreateRequest req, List<CollectionDtos.CreateTarget> targets, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && !(actor.role() == Role.EQUIPMENT_SUPPLIER && actor.isCompanyAdmin())) {
            throw ApiException.forbidden("NOT_EQUIPMENT_MASTER", "신규 등록형은 장비공급사 관리자 또는 관리자만 만들 수 있습니다");
        }
        Long targetCompanyId = req.targetCompanyId();
        if (targetCompanyId == null) throw ApiException.badRequest("TARGET_COMPANY_REQUIRED", "소유 협력업체를 선택하세요");
        if (actor.role() != Role.ADMIN && !companyService.selfAndChildren(actor.companyId()).contains(targetCompanyId)) {
            throw ApiException.forbidden("FORBIDDEN_TARGET_COMPANY", "본인/직속 협력업체만 지정할 수 있습니다");
        }

        for (CollectionDtos.CreateTarget t : targets) {
            if (t.ownerType() != OwnerType.EQUIPMENT && t.ownerType() != OwnerType.PERSON) {
                throw ApiException.badRequest("BAD_OWNER_TYPE", "지원하지 않는 대상 유형입니다: " + t.ownerType());
            }
            String planned = t.plannedType() == null ? "" : t.plannedType().trim();
            if (planned.isEmpty()) throw ApiException.badRequest("PLANNED_TYPE_REQUIRED", "등록할 종류(장비종류/역할)를 선택하세요");
            validatePlannedType(t.ownerType(), planned);
            int qty = t.quantity() == null ? 0 : t.quantity();
            if (qty < 1) throw ApiException.badRequest("QUANTITY_REQUIRED", "수량을 1 이상으로 지정하세요");
            if (nullToEmpty(t.requiredTypeIds()).isEmpty() && nullToEmpty(t.optionalTypeIds()).isEmpty()) {
                throw ApiException.badRequest("NO_TYPES", "수집할 서류를 1개 이상 선택하세요");
            }
        }

        DocumentCollectionRequest r = DocumentCollectionRequest.builder()
                .token(genToken())
                .tokenExpiresAt(LocalDateTime.now().plusDays(14))
                .supplierCompanyId(actor.companyId())
                .targetCompanyId(targetCompanyId)
                .createdBy(actor.id())
                .title(req.title())
                .recipientName(req.recipientName())
                .recipientPhone(req.recipientPhone())
                .recipientEmail(req.recipientEmail())
                .build();
        reqRepo.save(r);

        List<Long> typeIds = targets.stream()
                .flatMap(t -> java.util.stream.Stream.concat(nullToEmpty(t.requiredTypeIds()).stream(), nullToEmpty(t.optionalTypeIds()).stream()))
                .distinct().toList();
        Map<Long, DocumentType> typeById = new HashMap<>();
        for (DocumentType t : typeRepo.findAllById(typeIds)) typeById.put(t.getId(), t);

        // [종류 × 수량] → owner_id=null 슬롯 quantity 개. 배열 순서 = sort_order(공개 화면 노출 순서).
        int sort = 0;
        for (CollectionDtos.CreateTarget t : targets) {
            int qty = t.quantity();
            for (int n = 0; n < qty; n++) {
                DocumentCollectionTarget tg = targetRepo.save(DocumentCollectionTarget.builder()
                        .requestId(r.getId()).ownerType(t.ownerType()).ownerId(null)
                        .plannedType(t.plannedType().trim()).sortOrder(sort++).build());
                saveItems(typeById, tg, nullToEmpty(t.requiredTypeIds()), true);
                saveItems(typeById, tg, nullToEmpty(t.optionalTypeIds()), false);
            }
        }
        return toResponse(r);
    }

    /** 등록형 종류 유효성 — 장비=활성 장비종류 code, 인원=유효 PersonRole. */
    private void validatePlannedType(OwnerType ownerType, String planned) {
        if (ownerType == OwnerType.EQUIPMENT) {
            if (!equipmentTypes.existsActive(planned)) {
                throw ApiException.badRequest("PLANNED_TYPE_INVALID", "등록되지 않았거나 비활성 장비종류입니다: " + planned);
            }
        } else {
            try { PersonRole.valueOf(planned); }
            catch (IllegalArgumentException e) { throw ApiException.badRequest("PLANNED_TYPE_INVALID", "유효하지 않은 역할입니다: " + planned); }
        }
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

    /**
     * suggest 의 '유형판' — owner 자원 조회 없이 종류(장비종류 code / 역할)로만 필수/선택 서류를 반환.
     * 등록형 요청 폼에서 슬롯을 만들기 전 프리필용. 권한은 suggest 와 동일(WORKER 차단).
     */
    @Transactional(readOnly = true)
    public CollectionDtos.SuggestResponse suggestByType(OwnerType ownerType, String typeCode, PersonRole role, AuthenticatedUser actor) {
        if (actor.role() == Role.WORKER) throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        Map<Long, Boolean> requiredByTypeId;
        if (ownerType == OwnerType.EQUIPMENT) {
            if (typeCode == null || typeCode.isBlank()) throw ApiException.badRequest("TYPE_CODE_REQUIRED", "장비종류를 지정하세요");
            requiredByTypeId = equipmentDocReq.requiredByDocTypeId(typeCode);
        } else if (ownerType == OwnerType.PERSON) {
            if (role == null) throw ApiException.badRequest("ROLE_REQUIRED", "역할을 지정하세요");
            requiredByTypeId = personDocReq.requiredByDocTypeId(Set.of(role));
        } else {
            throw ApiException.badRequest("BAD_OWNER_TYPE", "지원하지 않는 대상 유형입니다: " + ownerType);
        }
        return split(requiredByTypeId, typeRepo.findByAppliesToAndActiveOrderBySortOrderAscIdAsc(ownerType, true));
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
                        t != null && com.skep.document.DocumentTypeService.sampleIsPdf(t),
                        t != null ? t.getSampleDescription() : null);
            }).toList();
            boolean registered = tg.getOwnerId() != null;
            String label = labels.get(tg.getId());
            return new CollectionDtos.PublicTarget(tg.getId(), tg.getOwnerType(), label,
                    tg.getPlannedType(), plannedTypeLabelOf(tg), registered,
                    tg.getOwnerType() == OwnerType.EQUIPMENT ? "VEHICLE_NO" : "NAME",
                    registered ? label : null,
                    own.size(), uploadedCount(own), requiredRemaining(own), pis);
        }).toList();

        return new CollectionDtos.PublicResponse(r.getTitle(), r.getRecipientName(), r.getStatus(), r.isExpired(),
                items.size(), uploadedCount(items), requiredRemaining(items), pts);
    }

    /** 항목(item) 단위 업로드 — 대상별로 같은 서류종류가 여러 건일 수 있어 documentTypeId 로는 특정 불가.
     *  파일 1개면 그대로, 2개 이상이면 올린 순서대로 1개 PDF로 병합해 저장(현장에서 여러 장 촬영 대응). */
    public void publicUpload(String token, Long itemId, MultipartFile[] files) {
        DocumentCollectionRequest r = requireToken(token);
        if (r.isExpired()) throw ApiException.badRequest("EXPIRED", "링크가 만료되었습니다");
        DocumentCollectionItem item = itemRepo.findById(itemId)
                .filter(it -> it.getRequestId().equals(r.getId()))
                .orElseThrow(() -> ApiException.badRequest("ITEM_NOT_IN_REQUEST", "요청에 없는 서류입니다"));
        DocumentCollectionTarget target = targetRepo.findById(item.getTargetId())
                .orElseThrow(() -> ApiException.badRequest("ITEM_NOT_IN_REQUEST", "요청에 없는 서류입니다"));
        if (target.getOwnerId() == null) {
            throw ApiException.badRequest("OWNER_NOT_REGISTERED",
                    target.getOwnerType() == OwnerType.EQUIPMENT ? "먼저 차량번호를 입력해 등록하세요" : "먼저 이름을 입력해 등록하세요");
        }
        MultipartFile file = uploadMerger.mergeToSingle(files);
        Document doc = documentService.uploadViaCollection(target.getOwnerType(), target.getOwnerId(),
                item.getDocumentTypeId(), null, file);
        item.attachDocument(doc.getId());
    }

    /**
     * 등록형 무로그인 등록 — 공개 링크에서 차량번호/이름 입력 순간 자원(장비/인력)을 신규 생성하고 슬롯에 연결.
     * owner(supplier)는 요청의 targetCompanyId 로 강제(토큰 유출돼도 그 회사 밖 생성 불가).
     * 이미 등록된 슬롯이면 OPEN 상태에서 값(오타)만 수정한다(붙은 서류는 owner_id 기준이라 보존).
     */
    public void publicRegister(String token, Long targetId, String value) {
        DocumentCollectionRequest r = requireToken(token);
        DocumentCollectionTarget tg = targetRepo.findById(targetId)
                .filter(t -> t.getRequestId().equals(r.getId()))
                .orElseThrow(() -> ApiException.badRequest("TARGET_NOT_IN_REQUEST", "요청에 없는 대상입니다"));
        if (tg.getPlannedType() == null) throw ApiException.badRequest("NOT_REGISTER_TARGET", "등록형 대상이 아닙니다");
        String v = value == null ? "" : value.trim();
        if (v.isBlank()) {
            throw ApiException.badRequest("VALUE_REQUIRED",
                    tg.getOwnerType() == OwnerType.EQUIPMENT ? "차량번호를 입력하세요" : "이름을 입력하세요");
        }
        if (tg.getOwnerId() != null) {
            // 재등록(오타 수정) — OPEN 에서 값만 수정. 이미 붙은 서류는 owner_id 기준이라 그대로 보존.
            if (!"OPEN".equals(r.getStatus())) throw ApiException.badRequest("NOT_OPEN", "수정할 수 없는 상태입니다");
            if (tg.getOwnerType() == OwnerType.EQUIPMENT) {
                equipmentRepo.findById(tg.getOwnerId()).ifPresent(e -> e.update(v, null, null, null, null));
            } else {
                personRepo.findById(tg.getOwnerId()).ifPresent(p ->
                        p.update(v, null, null, null, null, null, null, null, null, null, null, null, null));
            }
            return;
        }
        Long companyId = r.getTargetCompanyId();
        if (companyId == null) throw ApiException.badRequest("NO_TARGET_COMPANY", "소유 협력업체가 지정되지 않은 요청입니다");
        Long ownerId;
        if (tg.getOwnerType() == OwnerType.EQUIPMENT) {
            ownerId = equipmentService.createViaCollection(companyId, tg.getPlannedType(), v).getId();
        } else {
            ownerId = personService.createViaCollection(companyId, v, Set.of(PersonRole.valueOf(tg.getPlannedType()))).getId();
        }
        tg.linkOwner(ownerId);
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
        // 등록형 미등록 슬롯은 owner_id=null — findAllById 로 새지 않게 걸러낸다.
        List<Long> ids = refs.stream().filter(x -> x.type() == OwnerType.EQUIPMENT).map(OwnerRef::id)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Equipment> m = new HashMap<>();
        for (Equipment e : equipmentRepo.findAllById(ids)) m.put(e.getId(), e);
        return m;
    }

    private Map<Long, Person> personById(List<OwnerRef> refs) {
        List<Long> ids = refs.stream().filter(x -> x.type() == OwnerType.PERSON).map(OwnerRef::id)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Person> m = new HashMap<>();
        for (Person p : personRepo.findAllById(ids)) m.put(p.getId(), p);
        return m;
    }

    /** 등록형 슬롯의 종류 라벨 — 장비=장비종류 이름, 인원=역할 한글. plannedType 없으면 null. */
    private String plannedTypeLabelOf(DocumentCollectionTarget tg) {
        String code = tg.getPlannedType();
        if (code == null) return null;
        if (tg.getOwnerType() == OwnerType.EQUIPMENT) return equipmentTypes.labelOf(code);
        try { return personRoleLabel(PersonRole.valueOf(code)); }
        catch (IllegalArgumentException e) { return code; }
    }

    private static String personRoleLabel(PersonRole role) {
        return switch (role) {
            case OPERATOR -> "조종원";
            case WORK_DIRECTOR -> "작업지휘자";
            case GUIDE -> "유도원";
            case FIRE_WATCH -> "화기감시자";
            case SIGNALER -> "신호수";
            case INSPECTOR -> "점검원";
            case SITE_MANAGER -> "소장";
        };
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
            if (t.getOwnerId() == null) {
                // 등록형 미등록 슬롯 — 아직 자원이 없어 종류 라벨로 표시.
                String pl = plannedTypeLabelOf(t);
                label = pl != null ? pl : (t.getOwnerType() == OwnerType.EQUIPMENT ? "장비" : "인력");
            } else if (t.getOwnerType() == OwnerType.EQUIPMENT) {
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
        Map<Long, List<String>> supplierNames = supplierNamesByRequest(rows, tByReq);

        return rows.stream().map(r -> {
            List<DocumentCollectionTarget> ts = tByReq.getOrDefault(r.getId(), List.of());
            List<DocumentCollectionItem> is = iByReq.getOrDefault(r.getId(), List.of());
            return new CollectionDtos.SummaryResponse(r.getId(), r.getToken(), r.getTitle(),
                    ownerSummary(ts, labels),
                    r.getRecipientName(), r.getRecipientPhone(), r.getRecipientEmail(),
                    r.getStatus(), r.getCreatedAt(), r.getSubmittedAt(), r.getSentAt(),
                    publicBaseUrl + "/collect/" + r.getToken(),
                    ts.size(), is.size(), uploadedCount(is),
                    supplierNames.getOrDefault(r.getId(), List.of()));
        }).toList();
    }

    /**
     * 요청별 대상들의 소유 협력업체명(distinct, 대상 순서 보존).
     * 갱신형: 대상 장비/인원의 supplierId → 회사명. 등록형(미등록 슬롯, ownerId=null): 요청의 지정 협력업체(supplierCompanyId)로 대체.
     */
    private Map<Long, List<String>> supplierNamesByRequest(
            List<DocumentCollectionRequest> rows,
            Map<Long, List<DocumentCollectionTarget>> tByReq) {
        List<DocumentCollectionTarget> allTargets = tByReq.values().stream().flatMap(List::stream).toList();
        List<OwnerRef> refs = allTargets.stream().map(t -> new OwnerRef(t.getOwnerType(), t.getOwnerId())).toList();
        Map<Long, Equipment> equipment = equipmentById(refs);
        Map<Long, Person> persons = personById(refs);
        Set<Long> supplierIds = new HashSet<>();
        for (DocumentCollectionRequest r : rows) if (r.getSupplierCompanyId() != null) supplierIds.add(r.getSupplierCompanyId());
        for (DocumentCollectionTarget t : allTargets) {
            Long sid = targetSupplierId(t, equipment, persons);
            if (sid != null) supplierIds.add(sid);
        }
        Map<Long, String> names = new HashMap<>();
        for (Company c : companyRepo.findAllById(supplierIds)) names.put(c.getId(), c.getName());
        Map<Long, List<String>> out = new HashMap<>();
        for (DocumentCollectionRequest r : rows) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (DocumentCollectionTarget t : tByReq.getOrDefault(r.getId(), List.of())) {
                Long sid = targetSupplierId(t, equipment, persons);
                if (sid == null) sid = r.getSupplierCompanyId();
                String nm = sid != null ? names.get(sid) : null;
                if (nm != null) set.add(nm);
            }
            out.put(r.getId(), new ArrayList<>(set));
        }
        return out;
    }

    private static Long targetSupplierId(DocumentCollectionTarget t, Map<Long, Equipment> equipment, Map<Long, Person> persons) {
        if (t.getOwnerId() == null) return null;
        if (t.getOwnerType() == OwnerType.EQUIPMENT) {
            Equipment e = equipment.get(t.getOwnerId());
            return e != null ? e.getSupplierId() : null;
        }
        if (t.getOwnerType() == OwnerType.PERSON) {
            Person p = persons.get(t.getOwnerId());
            return p != null ? p.getSupplierId() : null;
        }
        return null;
    }
}
