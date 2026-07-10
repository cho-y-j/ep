package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
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

    public EquipmentService(EquipmentRepository repo, CompanyRepository companies,
                            com.skep.company.CompanyService companyService,
                            DocumentRepository docRepo, FileStorage storage,
                            SiteRepository sites, SiteParticipantRepository participants,
                            EquipmentDefaultOperatorRepository defaultOperators,
                            com.skep.person.PersonRepository personRepo,
                            com.skep.person.PersonService personService) {
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
    public List<Equipment> list(AuthenticatedUser actor, Long supplierIdParam, EquipmentCategory category) {
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
                return all.stream().filter(e -> e.getCategory() == category).toList();
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
            return category != null ? all.stream().filter(e -> e.getCategory() == category).toList() : all;
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
        Equipment e = Equipment.builder()
                .supplierId(supplierId)
                .vehicleNo(req.vehicleNo())
                .category(req.category())
                .model(req.model())
                .manufacturer(req.manufacturer())
                .year(req.year())
                .build();
        e.updateSourcing(req.isExternal(), req.vehicleOwnerName(), req.vehicleOwnerBusinessNo());
        // Phase4: 외부 장비 기사(조종원) 함께 등록 — 한 번에. (이름/아이디/비번 다 있으면)
        if (notBlank(req.operatorName()) && notBlank(req.operatorUsername()) && notBlank(req.operatorPassword())) {
            e.linkOperator(createOperatorPerson(supplierId, req.operatorName(), req.operatorPhone(),
                    req.operatorUsername(), req.operatorPassword(), actor));
        }
        return repo.save(e);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    public Equipment update(Long id, UpdateEquipmentRequest req, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        e.update(req.vehicleNo(), req.category(), req.model(), req.manufacturer(), req.year());
        e.updateSourcing(req.isExternal(), req.vehicleOwnerName(), req.vehicleOwnerBusinessNo());
        return e;
    }

    public void delete(Long id, AuthenticatedUser actor) {
        Equipment e = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "equipment " + id + " not found"));
        ensureCanModify(actor, e.getSupplierId());
        String photoKey = e.getPhotoKey();
        repo.delete(e);
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
            // EQUIPMENT_SUPPLIER가 다른 회사 ID 보내면 차단
            if (supplierIdParam != null && !supplierIdParam.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "다른 회사의 장비를 등록할 수 없습니다");
            }
            return actor.companyId();
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
        if (actor.role() == Role.EQUIPMENT_SUPPLIER && supplierId.equals(actor.companyId())) return;
        throw ApiException.forbidden("FORBIDDEN", "수정 권한이 없습니다");
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않은 사용자입니다 (재로그인 필요)");
        }
    }
}
