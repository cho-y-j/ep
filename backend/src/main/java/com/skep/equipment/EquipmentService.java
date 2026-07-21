package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.dto.CreateEquipmentRequest;
import com.skep.equipment.dto.UpdateEquipmentRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
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

@Service
@Transactional
public class EquipmentService {

    private static final int EXPIRING_DAYS = 30;

    private final EquipmentRepository repo;
    private final CompanyRepository companies;
    private final com.skep.company.CompanyService companyService;
    private final DocumentRepository docRepo;
    private final FileStorage storage;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final EquipmentDefaultOperatorRepository defaultOperators;
    private final com.skep.person.PersonRepository personRepo;
    private final com.skep.person.PersonService personService;
    private final EquipmentTypeService equipmentTypes;

    public EquipmentService(EquipmentRepository repo, CompanyRepository companies,
                            com.skep.company.CompanyService companyService,
                            DocumentRepository docRepo, FileStorage storage,
                            SiteRepository sites, SiteParticipantRepository participants,
                            EquipmentDefaultOperatorRepository defaultOperators,
                            com.skep.person.PersonRepository personRepo,
                            com.skep.person.PersonService personService,
                            EquipmentTypeService equipmentTypes) {
        this.repo = repo;
        this.companies = companies;
        this.companyService = companyService;
        this.docRepo = docRepo;
        this.storage = storage;
        this.sites = sites;
        this.participants = participants;
        this.defaultOperators = defaultOperators;
        this.personRepo = personRepo;
        this.personService = personService;
        this.equipmentTypes = equipmentTypes;
    }

    // ── V36: 장비 기본 조종원 ───────────────────────────────

    @Transactional(readOnly = true)
    public List<EquipmentDefaultOperator> listDefaultOperators(Long equipmentId, AuthenticatedUser actor) {
        Equipment e = repo.findById(equipmentId).orElseThrow(() ->
                ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 " + equipmentId + " 없음"));
        // 조회 권한 — 일반 get 과 동일 (장비 소유자, ADMIN, 또는 BP/관계자 등)
        get(equipmentId, actor);
        return defaultOperators.findByEquipmentIdOrderByPriorityAsc(e.getId());
    }

    /**
     * 장비 여러 대의 기본 조종원을 한 번에 — 서류수집 대상 다중선택(장비 1대 : 조종원 N명, 교대조) 용.
     * 반환은 equipmentId → personId 리스트(priority 오름차순). 기본 조종원이 없는 장비는
     * 단일 연결(operator_person_id)로 폴백하고, 그것도 없으면 결과에서 빠진다.
     * 권한은 장비당이 아니라 '서로 다른 공급사' 당 1회만 검사해 50대 선택에서도 쿼리가 폭증하지 않게 한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> defaultOperatorsByEquipmentIds(List<Long> equipmentIds, AuthenticatedUser actor) {
        if (equipmentIds == null || equipmentIds.isEmpty()) return Map.of();
        List<Equipment> found = repo.findAllById(equipmentIds);
        for (Long supplierId : found.stream().map(Equipment::getSupplierId).distinct().toList()) {
            ensureCanAccess(actor, supplierId);
        }
        Map<Long, List<Long>> out = new HashMap<>();
        for (EquipmentDefaultOperator o : defaultOperators
                .findByEquipmentIdInOrderByEquipmentIdAscPriorityAsc(found.stream().map(Equipment::getId).toList())) {
            out.computeIfAbsent(o.getEquipmentId(), k -> new java.util.ArrayList<>()).add(o.getPersonId());
        }
        for (Equipment e : found) {
            if (!out.containsKey(e.getId()) && e.getOperatorPersonId() != null) {
                out.put(e.getId(), List.of(e.getOperatorPersonId()));
            }
        }
        return out;
    }

    public List<EquipmentDefaultOperator> setDefaultOperators(Long equipmentId, List<Long> personIds, AuthenticatedUser actor) {
        Equipment e = repo.findById(equipmentId).orElseThrow(() ->
                ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 " + equipmentId + " 없음"));
        ensureCanModifyDefaultOperators(actor, e);
        // delete 후 같은 트랜잭션 안에서 새 insert 가 일어나면 영속성 컨텍스트가 delete 미반영 상태일 수 있어
        // UNIQUE 제약 위반. 명시적 flush 로 delete 를 DB 에 즉시 반영.
        defaultOperators.deleteByEquipmentId(e.getId());
        defaultOperators.flush();
        if (personIds == null || personIds.isEmpty()) return List.of();
        int pri = 1;
        List<EquipmentDefaultOperator> saved = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Long pid : personIds) {
            if (pid == null || !seen.add(pid)) continue;
            personRepo.findById(pid).orElseThrow(() ->
                    ApiException.badRequest("PERSON_NOT_FOUND", "인원 " + pid + " 없음"));
            // 인원 소속 회사 제약 + OPERATOR 역할 강제 모두 제거 — 어떤 인원을 매칭할지는 운영자가 결정.
            // 권한은 ensureCanModifyDefaultOperators 가 이미 actor 단위로 검증.
            saved.add(defaultOperators.save(EquipmentDefaultOperator.builder()
                    .equipmentId(e.getId()).personId(pid).priority(pri++).build()));
        }
        return saved;
    }

    /** 기본 조종원 매칭 가드. 일반 modify (장비 수정/삭제) 보다 관대 — BP 가 자기 사이트 참여 공급사 장비에도 매칭 가능. */
    private void ensureCanModifyDefaultOperators(AuthenticatedUser actor, Equipment e) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.companyId() != null && e.getSupplierId().equals(actor.companyId())) return;
        if (actor.role() == Role.BP && actor.companyId() != null) {
            List<Long> mySiteIds = sites.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream()
                    .map(Site::getId).toList();
            for (Long sid : mySiteIds) {
                if (participants.existsBySiteIdAndCompanyIdAndStatus(sid, e.getSupplierId(), SiteParticipantStatus.ACTIVE)) {
                    return;
                }
            }
        }
        throw ApiException.forbidden("FORBIDDEN", "기본 조종원 수정 권한이 없습니다");
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> expiringCountsByEquipmentIds(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        LocalDate maxDate = LocalDate.now().plusDays(EXPIRING_DAYS);
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : docRepo.countExpiringGroupedByOwner(OwnerType.EQUIPMENT, ids, maxDate)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    /**
     * 권한별 가시성 (S-3.1 정책 Equipment 적용):
     * - ADMIN: 전체.
     * - EQUIPMENT_SUPPLIER: 자기 회사 자원만.
     * - BP: 자기 사이트의 ACTIVE EQUIPMENT_SUPPLIER 참여 공급사 자원만.
     * - MANPOWER_SUPPLIER / WORKER: 차단 (장비 도메인은 인력 공급사가 다룰 일 없음).
     */
    @Transactional(readOnly = true)
    public List<Equipment> list(AuthenticatedUser actor, Long supplierIdParam, String category) {
        // BP 는 자기 사이트의 ACTIVE 장비 공급사로 list 를 좁힘.
        if (actor.role() == Role.BP) {
            requireCompany(actor);
            List<Long> visibleSupplierIds = visibleEquipmentSupplierIdsForBp(actor.companyId());
            if (visibleSupplierIds.isEmpty()) return List.of();
            // 명시 supplierIdParam 이 visible 안에 있어야 통과.
            if (supplierIdParam != null && !visibleSupplierIds.contains(supplierIdParam)) {
                return List.of();
            }
            List<Long> targetSuppliers = supplierIdParam != null ? List.of(supplierIdParam) : visibleSupplierIds;
            List<Equipment> all = repo.findBySupplierIdInOrderByIdDesc(targetSuppliers);
            if (category != null) {
                return all.stream().filter(e -> java.util.Objects.equals(e.getCategory(), category)).toList();
            }
            return all;
        }
        if (actor.role() == Role.MANPOWER_SUPPLIER || actor.role() == Role.WORKER) {
            throw ApiException.forbidden("EQUIPMENT_DENIED", "장비 도메인 조회 권한이 없습니다");
        }

        // V77: 장비공급사 목록은 본인 + 직속 자식(있으면) 자원 포함. 자식이 없으면 selfAndChildren = {본인} 이라 기존과 동일.
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            requireCompany(actor);
            List<Long> scope = companyService.selfAndChildren(actor.companyId());
            List<Long> targets = supplierIdParam != null
                    ? (scope.contains(supplierIdParam) ? List.of(supplierIdParam) : List.of())
                    : scope;
            if (targets.isEmpty()) return List.of();
            List<Equipment> all = repo.findBySupplierIdInOrderByIdDesc(targets);
            return category != null ? all.stream().filter(e -> java.util.Objects.equals(e.getCategory(), category)).toList() : all;
        }

        Long supplierId = resolveListSupplier(actor, supplierIdParam);

        if (supplierId != null && category != null) {
            return repo.findBySupplierIdAndCategoryOrderByIdDesc(supplierId, category);
        }
        if (supplierId != null) {
            return repo.findBySupplierIdOrderByIdDesc(supplierId);
        }
        if (category != null) {
            return repo.findByCategoryOrderByIdDesc(category);
        }
        return repo.findAllByOrderByIdDesc();
    }

    /** BP 시점에서 장비 조회 가능한 공급사 회사 ID 들.
     *  - 본인 회사 (#5 BP 직속 장비/차량)
     *  - 자기 사이트에 ACTIVE 참여 중인 EQUIPMENT_SUPPLIER */
    private List<Long> visibleEquipmentSupplierIdsForBp(Long bpCompanyId) {
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        ids.add(bpCompanyId);
        List<Long> siteIds = sites.findByBpCompanyIdOrderByIdDesc(bpCompanyId).stream()
                .map(Site::getId).toList();
        if (!siteIds.isEmpty()) {
            participants.findBySiteIdIn(siteIds).stream()
                    .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE
                            && p.getParticipantType() == com.skep.site.SiteParticipantType.EQUIPMENT_SUPPLIER)
                    .map(SiteParticipant::getCompanyId)
                    .forEach(ids::add);
        }
        return new java.util.ArrayList<>(ids);
    }

    @Transactional(readOnly = true)
    public Equipment get(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanAccess(actor, e.getSupplierId());
        return e;
    }

    /** Phase4: 외부 장비 기사(조종원) 등록 + 로그인 계정 발급(기존 Person 계정 시스템 재사용) + 장비 연결. */
    public Equipment registerOperator(Long equipmentId, com.skep.equipment.dto.RegisterOperatorRequest req, AuthenticatedUser actor) {
        Equipment e = repo.findById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + equipmentId + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        e.linkOperator(createOperatorPerson(e.getSupplierId(), req.name(), req.phone(), req.username(), req.password(), actor));
        return repo.save(e);
    }

    /** 외부 장비 기사(조종원) Person 생성(OPERATOR + 로그인 계정) → personId. 기존 Person 계정 시스템 재사용. */
    private Long createOperatorPerson(Long supplierId, String name, String phone, String username, String password, AuthenticatedUser actor) {
        com.skep.person.dto.CreatePersonRequest personReq = new com.skep.person.dto.CreatePersonRequest(
                supplierId, name, null, phone,
                java.util.Set.of(com.skep.person.PersonRole.OPERATOR),
                null, null, null, null, null, null, null, null, null,
                username, password);
        return personService.create(personReq, actor).getId();
    }

    public Equipment create(CreateEquipmentRequest req, AuthenticatedUser actor) {
        Long supplierId = resolveCreateSupplier(actor, req.supplierId());
        Company supplier = companies.findById(supplierId)
                .orElseThrow(() -> ApiException.badRequest("SUPPLIER_NOT_FOUND", "회사 " + supplierId + " 를 찾을 수 없습니다"));
        if (supplier.getType() != CompanyType.EQUIPMENT) {
            throw ApiException.badRequest("SUPPLIER_NOT_EQUIPMENT", "장비공급사 유형이 아닌 회사에 장비를 등록할 수 없습니다");
        }
        requireActiveCategory(req.category());
        Equipment e = Equipment.builder()
                .supplierId(supplierId)
                .vehicleNo(req.vehicleNo())
                .category(req.category())
                .model(req.model())
                .manufacturer(req.manufacturer())
                .year(req.year())
                .build();
        e.updateSourcing(req.isExternal(), req.vehicleOwnerName(), req.vehicleOwnerBusinessNo());
        // 기사(조종원) — 선택한 등록 인력(Person)을 장비에 연결. (있을 때만)
        if (req.operatorPersonId() != null) {
            // 존재 + 소속 스코프(actor 본인/직속 자식) 검증 후에만 연결. API 우회로 타사/유령 인력 지정 차단.
            com.skep.person.Person operator = personRepo.findById(req.operatorPersonId()).orElseThrow(() ->
                    ApiException.badRequest("PERSON_NOT_FOUND", "인원 " + req.operatorPersonId() + " 없음"));
            if (!companyService.selfAndChildren(actor.companyId()).contains(operator.getSupplierId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "다른 회사 소속 인력을 조종원으로 지정할 수 없습니다");
            }
            e.linkOperator(req.operatorPersonId());
        }
        // 검사만료일(정기검사 유효기간) — 폼 입력 또는 자동차등록증 OCR 자동채움. 있으면 저장(만료관리 반영).
        if (req.inspectionDueDate() != null && !req.inspectionDueDate().isBlank()) {
            try {
                e.setInspectionDueDate(java.time.LocalDate.parse(req.inspectionDueDate().trim()));
            } catch (java.time.format.DateTimeParseException ex) {
                throw ApiException.badRequest("INVALID_DATE", "검사만료일 형식이 올바르지 않습니다 (YYYY-MM-DD)");
            }
        }
        return repo.save(e);
    }

    public Equipment update(Long id, UpdateEquipmentRequest req, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        if (req.category() != null) requireActiveCategory(req.category());
        e.update(req.vehicleNo(), req.category(), req.model(), req.manufacturer(), req.year());
        e.updateSourcing(req.isExternal(), req.vehicleOwnerName(), req.vehicleOwnerBusinessNo());
        return e;
    }

    public void delete(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        // 소유 서류(다형 owner_type/owner_id — FK 캐스케이드 없음)를 함께 삭제해 고아 문서·파일 방지.
        List<Document> docs = docRepo.findByOwnerTypeAndOwnerIdOrderByIdDesc(OwnerType.EQUIPMENT, id);
        docRepo.deleteAll(docs);
        String photoKey = e.getPhotoKey();
        repo.delete(e);
        docs.forEach(d -> storage.delete(d.getFileKey()));
        if (photoKey != null) storage.delete(photoKey);
    }

    public Equipment uploadPhoto(Long id, MultipartFile file, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());

        com.skep.common.ImageUploadValidator.validateOrThrow(file);
        String contentType = file.getContentType();

        String oldKey = e.getPhotoKey();
        String newKey = storage.store(file);
        e.setPhoto(newKey, contentType);
        if (oldKey != null) storage.delete(oldKey);
        return e;
    }

    public Equipment deletePhoto(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());

        String oldKey = e.getPhotoKey();
        e.clearPhoto();
        if (oldKey != null) storage.delete(oldKey);
        return e;
    }

    @Transactional(readOnly = true)
    public PhotoData loadPhoto(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanAccess(actor, e.getSupplierId());
        if (e.getPhotoKey() == null) {
            throw ApiException.notFound("PHOTO_NOT_FOUND", "사진이 등록되지 않았습니다");
        }
        return new PhotoData(storage.load(e.getPhotoKey()), e.getPhotoContentType());
    }

    public record PhotoData(Resource resource, String contentType) {}

    /** ADMIN: 어떤 supplier도 OK. EQUIPMENT_SUPPLIER: 본인 회사 강제. 그 외: 본인 회사 read-only 가정. */
    private Long resolveListSupplier(AuthenticatedUser actor, Long supplierIdParam) {
        if (actor.role() == Role.ADMIN) {
            return supplierIdParam;
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            requireCompany(actor);
            return actor.companyId();
        }
        // BP 등은 supplier_id 명시 시 그 회사만 보고, 없으면 전체 (장비 검색용)
        return supplierIdParam;
    }

    private Long resolveCreateSupplier(AuthenticatedUser actor, Long supplierIdParam) {
        if (actor.role() == Role.ADMIN) {
            if (supplierIdParam == null) {
                throw ApiException.badRequest("SUPPLIER_REQUIRED", "supplier_id 가 필요합니다 (ADMIN)");
            }
            return supplierIdParam;
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            requireCompany(actor);
            if (supplierIdParam == null) return actor.companyId();
            // V77: 본인 또는 직속 자식(협력사) 소유로 대행 등록 허용. 그 외 회사는 차단.
            if (!companyService.selfAndChildren(actor.companyId()).contains(supplierIdParam)) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "다른 회사의 장비를 등록할 수 없습니다");
            }
            return supplierIdParam;
        }
        throw ApiException.forbidden("ROLE_NOT_ALLOWED", "장비 등록 권한이 없는 역할입니다");
    }

    private void ensureCanAccess(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            // V77: 읽기만 본인 + 직속 자식 확장(부모→자식 단방향). 자식/형제는 selfAndChildren={본인} 이라 자동 차단.
            if (!companyService.selfAndChildren(actor.companyId()).contains(supplierId)) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사의 장비만 조회 가능합니다");
            }
            return;
        }
        if (actor.role() == Role.BP) {
            requireCompany(actor);
            if (!visibleEquipmentSupplierIdsForBp(actor.companyId()).contains(supplierId)) {
                throw ApiException.forbidden("EQUIPMENT_VIEW_DENIED",
                        "자기 사이트 ACTIVE 참여공급사 자원만 조회할 수 있습니다");
            }
            return;
        }
        // MANPOWER_SUPPLIER / WORKER 는 장비 도메인 차단.
        throw ApiException.forbidden("EQUIPMENT_DENIED", "장비 조회 권한이 없습니다");
    }

    private void ensureCanModify(AuthenticatedUser actor, Long supplierId) {
        if (actor.role() == Role.ADMIN) return;
        // V77: 쓰기(수정/삭제)도 본인 + 직속 자식(협력사) 확장 — 부모가 자식 장비 대행 수정/삭제.
        // selfAndChildren 은 부모→자식 단방향(자식이면 {본인})이라 자식→부모/형제/타사는 자동 403.
        if (actor.role() == Role.EQUIPMENT_SUPPLIER
                && companyService.selfAndChildren(actor.companyId()).contains(supplierId)) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않은 사용자입니다 (재로그인 필요)");
        }
    }

    /** 장비 종류 코드가 활성 마스터(equipment_type)에 존재하는지 검증 — enum 이 주던 역직렬화 게이트 대체. */
    private void requireActiveCategory(String code) {
        if (!equipmentTypes.existsActive(code)) {
            throw ApiException.badRequest("EQUIPMENT_CATEGORY_INVALID",
                    "등록되지 않았거나 비활성 상태인 장비 종류입니다: " + code);
        }
    }
}
