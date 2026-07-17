package com.skep.workplan;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.assignment.EquipmentAssignment;
import com.skep.assignment.EquipmentAssignmentRepository;
import com.skep.assignment.PersonAssignment;
import com.skep.assignment.PersonAssignmentRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.equipment.dto.EquipmentResponse;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.person.dto.PersonResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteParticipantType;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.workplan.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 작업계획서 도메인 서비스.
 *
 * 핵심 정책:
 * - 생성/수정: ADMIN 또는 BP (자기 회사 소유 사이트만).
 * - 자원 추가:
 *   1) 자원 supplier 가 사이트의 ACTIVE 참여공급사여야 함.
 *   2) 서류 컴플라이언스: blocks_assignment=true 필수 서류가 chain head + VERIFIED + 안만료 인지 확인.
 *      누락 시 ComplianceStatus=BLOCKED — override 없으면 거부.
 *   3) 만료 임박 (≤30일) 인 서류가 하나라도 있으면 WARNING (저장은 됨).
 *   4) 추가 결과를 work_plan_compliance_checks 에 스냅샷.
 * - 상태 전이: DRAFT → SUBMITTED → APPROVED → DONE 또는 CANCELLED.
 *   자원 편집은 DRAFT 단계에서만.
 * - 조회: ADMIN 전체, BP 자기 회사, 공급사 자기 회사 자원이 포함된 계획.
 */
@Service
@Transactional
public class WorkPlanService {

    private static final int EXPIRING_DAYS = 30;

    private final WorkPlanRepository wpRepo;
    private final WorkPlanEquipmentRepository wpeRepo;
    private final WorkPlanPersonRepository wppRepo;
    private final WorkPlanComplianceCheckRepository wpccRepo;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final CompanyRepository companies;
    private final EquipmentRepository equipmentRepo;
    private final com.skep.equipment.EquipmentDocRequirementService equipDocReq;
    private final com.skep.person.PersonDocRequirementService personDocReq;
    private final PersonRepository personRepo;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository docTypeRepo;
    private final EquipmentAssignmentRepository eqAssignments;
    private final PersonAssignmentRepository personAssignments;
    private final AuditLogService auditLog;
    /** S-11: BP 회사 사업자등록증 게이트 (작업계획서 생성 차단). @Lazy 로 순환 의존 회피. */
    private final com.skep.compliance.ComplianceService bpBizCertGate;
    private final com.skep.compliance.ComplianceOrderService complianceOrderService;
    /** S-12: 제출 시 5개 사인 검증. @Lazy 로 순환 의존 회피. */
    private final com.skep.signature.SignatureService signatureService;
    /** G-1: 작업 시작 시 자원별 안전점검 COMPLETED 여부 검증. */
    private final com.skep.safety.SafetyInspectionRepository safetyInspectionRepo;
    /** Auto-Res: 견적 dispatched 자원을 wp 에 자동 추가. */
    private final com.skep.quotation.dispatch.DispatchedEquipmentRepository dispatchedEqRepo;
    private final com.skep.quotation.dispatch.DispatchedPersonRepository dispatchedPersonRepo;
    /** DocReq: 제출 시점 점검 모두 APPROVED 검증. */
    private final com.skep.resourceCheck.ResourceCheckRequestRepository resourceCheckRepo;
    /** 누락 서류 endpoint — 이미 OPEN 인 보완요청 status 표시. */
    private final com.skep.supplement.DocumentSupplementRequestRepository supplementRepo;

    public WorkPlanService(WorkPlanRepository wpRepo, WorkPlanEquipmentRepository wpeRepo,
                           WorkPlanPersonRepository wppRepo, WorkPlanComplianceCheckRepository wpccRepo,
                           SiteRepository sites, SiteParticipantRepository participants,
                           CompanyRepository companies,
                           EquipmentRepository equipmentRepo,
                           com.skep.equipment.EquipmentDocRequirementService equipDocReq,
                           com.skep.person.PersonDocRequirementService personDocReq,
                           PersonRepository personRepo,
                           DocumentRepository docRepo, DocumentTypeRepository docTypeRepo,
                           EquipmentAssignmentRepository eqAssignments,
                           PersonAssignmentRepository personAssignments,
                           AuditLogService auditLog,
                           @org.springframework.context.annotation.Lazy
                           com.skep.compliance.ComplianceService bpBizCertGate,
                           @org.springframework.context.annotation.Lazy
                           com.skep.signature.SignatureService signatureService,
                           com.skep.safety.SafetyInspectionRepository safetyInspectionRepo,
                           @org.springframework.context.annotation.Lazy
                           com.skep.compliance.ComplianceOrderService complianceOrderService,
                           com.skep.quotation.dispatch.DispatchedEquipmentRepository dispatchedEqRepo,
                           com.skep.quotation.dispatch.DispatchedPersonRepository dispatchedPersonRepo,
                           com.skep.resourceCheck.ResourceCheckRequestRepository resourceCheckRepo,
                           com.skep.supplement.DocumentSupplementRequestRepository supplementRepo) {
        this.wpRepo = wpRepo;
        this.wpeRepo = wpeRepo;
        this.wppRepo = wppRepo;
        this.wpccRepo = wpccRepo;
        this.sites = sites;
        this.participants = participants;
        this.companies = companies;
        this.equipmentRepo = equipmentRepo;
        this.equipDocReq = equipDocReq;
        this.personDocReq = personDocReq;
        this.personRepo = personRepo;
        this.docRepo = docRepo;
        this.docTypeRepo = docTypeRepo;
        this.eqAssignments = eqAssignments;
        this.personAssignments = personAssignments;
        this.auditLog = auditLog;
        this.bpBizCertGate = bpBizCertGate;
        this.signatureService = signatureService;
        this.safetyInspectionRepo = safetyInspectionRepo;
        this.complianceOrderService = complianceOrderService;
        this.dispatchedEqRepo = dispatchedEqRepo;
        this.dispatchedPersonRepo = dispatchedPersonRepo;
        this.resourceCheckRepo = resourceCheckRepo;
        this.supplementRepo = supplementRepo;
    }

    // ================== CRUD ==================

    public WorkPlan create(CreateWorkPlanRequest req, AuthenticatedUser actor) {
        // 현장 옵션화 — siteId 있으면 거기서 BP, 없으면 req.bpCompanyId() 사용.
        Site site = req.siteId() != null
                ? sites.findById(req.siteId())
                        .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"))
                : null;
        Long bpCompanyId = site != null ? site.getBpCompanyId() : req.bpCompanyId();
        if (bpCompanyId == null) {
            throw ApiException.badRequest("BP_REQUIRED", "현장을 선택하지 않은 경우 BP 회사를 지정해야 합니다");
        }
        ensureCanManageBp(actor, bpCompanyId);

        // S-11: BP 회사 사업자등록증 VERIFIED 게이트
        if (bpBizCertGate != null && !bpBizCertGate.isBpBizCertVerified(bpCompanyId)) {
            throw ApiException.badRequest("BP_BIZ_CERT_REQUIRED",
                    "BP 회사의 사업자 등록증이 검증돼야 작업계획서를 만들 수 있습니다. /my-company 에서 업로드 + 검증 완료 후 다시 시도해주세요.");
        }

        WorkPlan wp = wpRepo.save(WorkPlan.builder()
                .siteId(site != null ? site.getId() : null)
                .bpCompanyId(bpCompanyId)
                .workDate(req.workDate())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .title(req.title())
                .workLocation(req.workLocation())
                .description(req.description())
                .createdBy(actor.id())
                .build());

        auditLog.record(actor, AuditAction.WORK_PLAN_CREATED, AuditTargetType.WORK_PLAN,
                wp.getId(), bpCompanyId, site != null ? site.getId() : null,
                null,
                "{\"title\":\"" + escape(wp.getTitle()) + "\",\"work_date\":\"" + wp.getWorkDate() + "\"}");

        // 견적에서 이어만들기 — dispatched 자원 자동 추가
        if (req.fromQuotationRequestId() != null) {
            autoAddDispatchedResources(wp.getId(), req.fromQuotationRequestId());
        }
        return wp;
    }

    /** 견적의 dispatched 차량/인원을 wp 에 자동 추가. ALREADY_ADDED 또는 site 제약 위반 시 그 항목만 skip. */
    private void autoAddDispatchedResources(Long workPlanId, Long quotationRequestId) {
        var dispatched = dispatchedEqRepo.findByQuotationRequestId(quotationRequestId);
        for (var d : dispatched) {
            if (wpeRepo.findByWorkPlanIdAndEquipmentId(workPlanId, d.getEquipmentId()).isPresent()) continue;
            wpeRepo.save(WorkPlanEquipment.builder()
                    .workPlanId(workPlanId)
                    .equipmentId(d.getEquipmentId())
                    .supplierCompanyId(d.getSupplierCompanyId())
                    .build());
        }
        var dispatchedP = dispatchedPersonRepo.findByQuotationRequestId(quotationRequestId);
        for (var d : dispatchedP) {
            if (wppRepo.findByWorkPlanIdAndPersonId(workPlanId, d.getPersonId()).isPresent()) continue;
            wppRepo.save(WorkPlanPerson.builder()
                    .workPlanId(workPlanId)
                    .personId(d.getPersonId())
                    .supplierCompanyId(d.getSupplierCompanyId())
                    .build());
        }
    }

    public WorkPlan update(Long id, UpdateWorkPlanRequest req, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (!wp.isEditable()) {
            throw ApiException.badRequest("WORK_PLAN_NOT_EDITABLE",
                    "DRAFT 상태에서만 수정할 수 있습니다 (현재: " + wp.getStatus() + ")");
        }
        wp.update(req.workDate(), req.startTime(), req.endTime(),
                req.title(), req.workLocation(), req.description());
        auditLog.record(actor, AuditAction.WORK_PLAN_UPDATED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(), null,
                "{\"title\":\"" + escape(wp.getTitle()) + "\"}");
        return wp;
    }

    /** S-9-B: skep 워크시트 폼 상태 (formValues + supplier context) 저장. DRAFT 단계에서만 수정 가능. */
    public WorkPlan updateFormValues(Long id, UpdateFormValuesRequest req, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (!wp.isEditable()) {
            throw ApiException.badRequest("WORK_PLAN_NOT_EDITABLE",
                    "DRAFT 상태에서만 워크시트 폼을 수정할 수 있습니다 (현재: " + wp.getStatus() + ")");
        }
        if (req.formValues() != null) {
            wp.setFormValues(req.formValues());
        }
        // supplier context 검증: 작업계획서에 실제로 배정된 자원의 supplier 인지 확인.
        validateSupplierContext(wp, req.equipmentSupplierCompanyId(),
                req.manpowerSupplierCompanyId(), req.currentEquipmentId());
        wp.setSupplierContext(
                req.equipmentSupplierCompanyId(),
                req.manpowerSupplierCompanyId(),
                req.currentEquipmentId());
        auditLog.record(actor, AuditAction.WORK_PLAN_UPDATED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(), null,
                "{\"formValuesUpdated\":true}");
        return wp;
    }

    /**
     * formValues 의 supplier/equipment ID 가 실제 작업계획서 배정 자원과 일치하는지 검증.
     * DOCX/PDF/서명 context 가 엉뚱한 회사·장비로 오염되는 것을 차단.
     * null 은 허용 (still empty form 단계).
     */
    private void validateSupplierContext(WorkPlan wp, Long equipmentSupplierCompanyId,
                                          Long manpowerSupplierCompanyId, Long currentEquipmentId) {
        var wpeRows = wpeRepo.findByWorkPlanIdOrderByIdAsc(wp.getId());
        var wppRows = wppRepo.findByWorkPlanIdOrderByIdAsc(wp.getId());
        if (currentEquipmentId != null) {
            boolean ok = wpeRows.stream().anyMatch(we -> currentEquipmentId.equals(we.getEquipmentId()));
            if (!ok) {
                throw ApiException.badRequest("INVALID_EQUIPMENT_CONTEXT",
                        "작업계획서에 배정되지 않은 장비입니다");
            }
        }
        if (equipmentSupplierCompanyId != null) {
            var equipmentIds = wpeRows.stream().map(we -> we.getEquipmentId()).toList();
            boolean ok = !equipmentIds.isEmpty() && equipmentRepo.findAllById(equipmentIds).stream()
                    .anyMatch(e -> equipmentSupplierCompanyId.equals(e.getSupplierId()));
            if (!ok) {
                throw ApiException.badRequest("INVALID_EQUIPMENT_SUPPLIER",
                        "작업계획서 배정 장비의 공급사가 아닙니다");
            }
        }
        if (manpowerSupplierCompanyId != null) {
            var personIds = wppRows.stream().map(wpp -> wpp.getPersonId()).toList();
            boolean ok = !personIds.isEmpty() && personRepo.findAllById(personIds).stream()
                    .anyMatch(p -> manpowerSupplierCompanyId.equals(p.getSupplierId()));
            if (!ok) {
                throw ApiException.badRequest("INVALID_MANPOWER_SUPPLIER",
                        "작업계획서 배정 인원의 공급사가 아닙니다");
            }
        }
    }

    /**
     * 작업계획서 복제 — 새 DRAFT 생성 + 원본의 장비/인원 행 그대로 복사 + 자원별 컴플라이언스 재평가 스냅샷 저장.
     *
     * - 권한: 원본을 관리할 수 있는 사람만 (`ensureCanManage`).
     * - 새 work_date: 요청 값 또는 원본 + 1일.
     * - 새 title: 요청 값 또는 "[복사] " + 원본 제목.
     * - 자원 복사 시 supplier 가 여전히 ACTIVE 참여인지 확인 — 아니면 그 행 skip.
     * - 컴플라이언스: 재평가하되 BLOCKED 라도 throw 하지 않고 BLOCKED 스냅샷만 남김 (DRAFT 라 제출 전 수정 가능).
     */
    public WorkPlan clone(Long sourceId, CloneWorkPlanRequest req, AuthenticatedUser actor) {
        WorkPlan src = getOrThrow(sourceId);
        Site site = siteOrThrow(src.getSiteId());
        ensureCanManage(actor, src);

        LocalDate newDate = req.workDate() != null ? req.workDate() : src.getWorkDate().plusDays(1);
        String newTitle = (req.title() != null && !req.title().isBlank())
                ? req.title()
                : "[복사] " + src.getTitle();

        WorkPlan wp = wpRepo.save(WorkPlan.builder()
                .siteId(src.getSiteId())
                .bpCompanyId(src.getBpCompanyId())
                .workDate(newDate)
                .startTime(src.getStartTime())
                .endTime(src.getEndTime())
                .title(newTitle)
                .workLocation(src.getWorkLocation())
                .description(src.getDescription())
                .createdBy(actor.id())
                .build());

        // 사이트의 ACTIVE 참여 공급사 set (자원 복사 시 필터링). site=null 이면 필터 안 함.
        java.util.Set<Long> activeSupplierIds = site == null ? null
                : participants.findBySiteIdOrderByIdDesc(site.getId()).stream()
                        .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                        .map(SiteParticipant::getCompanyId)
                        .collect(java.util.stream.Collectors.toSet());

        int copiedEquip = 0, skippedInactiveEquip = 0, skippedBlockedEquip = 0;
        int copiedPerson = 0, skippedInactivePerson = 0, skippedBlockedPerson = 0, droppedMatch = 0;

        // S-7.1 P1: BLOCKED 자원은 복제하지 않는다 (snapshot 도 남기지 않음 — 안 들어왔으니 추적할 target 없음).
        // 어떤 장비가 실제 복제됐는지 추적해서 P2: 인원의 매칭 장비 정합 검사에 사용.
        Set<Long> copiedEquipmentIds = new HashSet<>();

        for (WorkPlanEquipment srcWpe : wpeRepo.findByWorkPlanIdOrderByIdAsc(sourceId)) {
            if (activeSupplierIds != null && !activeSupplierIds.contains(srcWpe.getSupplierCompanyId())) {
                skippedInactiveEquip++;
                continue;
            }
            ComplianceResult r = evaluateForClone(OwnerType.EQUIPMENT, srcWpe.getEquipmentId());
            if (r.status() == ComplianceStatus.BLOCKED) {
                skippedBlockedEquip++;
                continue;
            }
            wpeRepo.save(WorkPlanEquipment.builder()
                    .workPlanId(wp.getId())
                    .equipmentId(srcWpe.getEquipmentId())
                    .supplierCompanyId(srcWpe.getSupplierCompanyId())
                    .purpose(srcWpe.getPurpose())
                    .note(srcWpe.getNote())
                    .build());
            recordCompliance(wp.getId(), OwnerType.EQUIPMENT, srcWpe.getEquipmentId(), r, actor);
            copiedEquipmentIds.add(srcWpe.getEquipmentId());
            copiedEquip++;
        }

        for (WorkPlanPerson srcWpp : wppRepo.findByWorkPlanIdOrderByIdAsc(sourceId)) {
            if (activeSupplierIds != null && !activeSupplierIds.contains(srcWpp.getSupplierCompanyId())) {
                skippedInactivePerson++;
                continue;
            }
            ComplianceResult r = evaluateForClone(OwnerType.PERSON, srcWpp.getPersonId());
            if (r.status() == ComplianceStatus.BLOCKED) {
                skippedBlockedPerson++;
                continue;
            }
            // S-7.1 P2: 매칭 장비가 이번 복제에서 빠졌으면 매칭을 끊고 (null) 인원만 그대로 복사.
            Long matchEquipmentId = srcWpp.getEquipmentId();
            if (matchEquipmentId != null && !copiedEquipmentIds.contains(matchEquipmentId)) {
                matchEquipmentId = null;
                droppedMatch++;
            }
            wppRepo.save(WorkPlanPerson.builder()
                    .workPlanId(wp.getId())
                    .personId(srcWpp.getPersonId())
                    .supplierCompanyId(srcWpp.getSupplierCompanyId())
                    .equipmentId(matchEquipmentId)
                    .role(srcWpp.getRole())
                    .note(srcWpp.getNote())
                    .build());
            recordCompliance(wp.getId(), OwnerType.PERSON, srcWpp.getPersonId(), r, actor);
            copiedPerson++;
        }

        auditLog.record(actor, AuditAction.WORK_PLAN_CLONED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(), null,
                "{\"source_id\":" + sourceId
                        + ",\"copied_equipment\":" + copiedEquip
                        + ",\"skipped_inactive_equipment\":" + skippedInactiveEquip
                        + ",\"skipped_blocked_equipment\":" + skippedBlockedEquip
                        + ",\"copied_person\":" + copiedPerson
                        + ",\"skipped_inactive_person\":" + skippedInactivePerson
                        + ",\"skipped_blocked_person\":" + skippedBlockedPerson
                        + ",\"dropped_equipment_match\":" + droppedMatch + "}");
        return wp;
    }

    /** 복제 전용: 컴플라이언스 평가하되 BLOCKED 라도 throw 하지 않고 BLOCKED 결과만 반환. */
    private ComplianceResult evaluateForClone(OwnerType ownerType, Long ownerId) {
        List<DocumentType> blocking = docTypeRepo
                .findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc(ownerType);
        // 인원: 역할 한정 / 장비: 카테고리 한정 서류만 필터링
        if (ownerType == OwnerType.PERSON) {
            Person p = personRepo.findById(ownerId).orElse(null);
            Set<com.skep.person.PersonRole> personRoles = p != null ? p.getRoles() : Set.of();
            // PERSON 적용여부는 역할×서류 junction(행 존재) 기준. 역할 미보유(비영속)만 기존 CSV fallback.
            java.util.Set<Long> applicableTypeIds = !personRoles.isEmpty() ? personDocReq.applicableDocTypeIds(personRoles) : null;
            blocking = blocking.stream()
                    .filter(t -> applicableTypeIds != null
                            ? applicableTypeIds.contains(t.getId())
                            : matchesPersonRoles(t.getAppliesToPersonRoles(), personRoles))
                    .toList();
        } else if (ownerType == OwnerType.EQUIPMENT) {
            com.skep.equipment.Equipment e = equipmentRepo.findById(ownerId).orElse(null);
            String cat = e != null ? e.getCategory() : null;
            // EQUIPMENT 적용여부는 종류×서류 junction(행 존재) 기준. cat==null(비영속)만 기존 CSV fallback.
            java.util.Set<Long> applicableTypeIds = cat != null ? equipDocReq.applicableDocTypeIds(cat) : null;
            blocking = blocking.stream()
                    .filter(t -> applicableTypeIds != null
                            ? applicableTypeIds.contains(t.getId())
                            : matchesEquipmentCategory(t.getAppliesToCategories(), cat))
                    .toList();
        }
        if (blocking.isEmpty()) return new ComplianceResult(ComplianceStatus.OK, null, List.of());

        Set<Long> blockingIds = new HashSet<>();
        for (DocumentType t : blocking) blockingIds.add(t.getId());

        LocalDate today = LocalDate.now();
        Set<Long> validIds = new HashSet<>();
        for (Object[] row : docRepo.findValidVerifiedTypesByOwners(ownerType, List.of(ownerId), today)) {
            Long typeId = (Long) row[1];
            if (blockingIds.contains(typeId)) validIds.add(typeId);
        }
        List<String> missing = blocking.stream()
                .filter(t -> !validIds.contains(t.getId()))
                .map(DocumentType::getName)
                .toList();
        if (!missing.isEmpty()) {
            return new ComplianceResult(ComplianceStatus.BLOCKED,
                    "복제 시점 필수 서류 누락/만료/반려: " + String.join(", ", missing), missing);
        }
        long expiringCount = docRepo.findRiskyForOwners(ownerType, List.of(ownerId),
                today.plusDays(EXPIRING_DAYS)).size();
        if (expiringCount > 0) {
            return new ComplianceResult(ComplianceStatus.WARNING,
                    "만료 임박 또는 검토 필요 서류 " + expiringCount + "건", List.of());
        }
        return new ComplianceResult(ComplianceStatus.OK, null, List.of());
    }

    @Transactional(readOnly = true)
    public WorkPlanResponse get(Long id, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        ensureCanView(actor, wp);
        return toDetailResponse(wp);
    }

    @Transactional(readOnly = true)
    public Page<WorkPlanResponse> list(AuthenticatedUser actor, int page, int size) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size, Sort.unsorted());

        Page<WorkPlan> pg;
        if (actor.role() == Role.ADMIN) {
            pg = wpRepo.findAllByOrderByWorkDateAscIdAsc(pageable);
        } else if (actor.role() == Role.BP) {
            requireCompany(actor);
            pg = wpRepo.findByBpCompanyIdOrderByWorkDateAscIdAsc(actor.companyId(), pageable);
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            requireCompany(actor);
            pg = wpRepo.findBySupplierCompanyId(actor.companyId(), pageable);
        } else {
            return Page.empty(pageable);
        }

        // site/bp 이름 캐시
        List<Long> siteIds = pg.getContent().stream().map(WorkPlan::getSiteId).distinct().toList();
        List<Long> bpIds = pg.getContent().stream().map(WorkPlan::getBpCompanyId).distinct().toList();
        Map<Long, Site> siteMap = mapById(sites.findAllById(siteIds), Site::getId);
        Map<Long, Company> bpMap = mapById(companies.findAllById(bpIds), Company::getId);

        return pg.map(wp -> WorkPlanResponse.summary(
                wp,
                Optional.ofNullable(siteMap.get(wp.getSiteId())).map(Site::getName).orElse(null),
                Optional.ofNullable(bpMap.get(wp.getBpCompanyId())).map(Company::getName).orElse(null)
        ));
    }

    // ================== 자원 추가/제거 ==================

    public WorkPlanResponse addEquipment(Long workPlanId, AddEquipmentRequest req, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(workPlanId);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (!wp.isEditable()) {
            throw ApiException.badRequest("WORK_PLAN_NOT_EDITABLE", "DRAFT 상태에서만 자원 편집 가능");
        }

        Equipment e = equipmentRepo.findById(req.equipmentId())
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 없음"));
        if (site != null) ensureSupplierIsParticipant(site.getId(), e.getSupplierId());
        // S-12+: 장비공급사 회사 사업자등록증 VERIFIED 게이트.
        if (bpBizCertGate != null && !bpBizCertGate.isSupplierBizCertVerified(e.getSupplierId())) {
            throw ApiException.badRequest("SUPPLIER_BIZ_CERT_UNVERIFIED",
                    "장비공급사 사업자등록증이 검증되어야 작업계획서에 자원을 추가할 수 있습니다");
        }
        if (wpeRepo.findByWorkPlanIdAndEquipmentId(workPlanId, e.getId()).isPresent()) {
            throw ApiException.badRequest("ALREADY_ADDED", "이미 추가된 장비입니다");
        }

        // 서류 컴플라이언스 검사 + 스냅샷 저장 + override 처리.
        ComplianceResult compliance = evaluateCompliance(OwnerType.EQUIPMENT, e.getId(), req.override(), req.overrideReason(), actor);
        recordCompliance(wp.getId(), OwnerType.EQUIPMENT, e.getId(), compliance, actor);

        wpeRepo.save(WorkPlanEquipment.builder()
                .workPlanId(workPlanId)
                .equipmentId(e.getId())
                .supplierCompanyId(e.getSupplierId())
                .purpose(req.purpose())
                .note(req.note())
                .build());

        auditLog.record(actor, AuditAction.WORK_PLAN_EQUIPMENT_ADDED, AuditTargetType.WORK_PLAN_EQUIPMENT,
                e.getId(), e.getSupplierId(), wp.getSiteId(), null,
                "{\"work_plan_id\":" + workPlanId + ",\"compliance\":\"" + compliance.status.name() + "\"}");

        return toDetailResponse(wp);
    }

    public WorkPlanResponse removeEquipment(Long workPlanId, Long equipmentId, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(workPlanId);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (!wp.isEditable()) {
            throw ApiException.badRequest("WORK_PLAN_NOT_EDITABLE", "DRAFT 상태에서만 자원 편집 가능");
        }
        WorkPlanEquipment row = wpeRepo.findByWorkPlanIdAndEquipmentId(workPlanId, equipmentId)
                .orElseThrow(() -> ApiException.notFound("WP_EQUIPMENT_NOT_FOUND", "장비가 추가되어 있지 않습니다"));
        wpeRepo.delete(row);
        auditLog.record(actor, AuditAction.WORK_PLAN_EQUIPMENT_REMOVED, AuditTargetType.WORK_PLAN_EQUIPMENT,
                equipmentId, row.getSupplierCompanyId(), wp.getSiteId(),
                "{\"work_plan_id\":" + workPlanId + "}", null);
        return toDetailResponse(wp);
    }

    public WorkPlanResponse addPerson(Long workPlanId, AddPersonRequest req, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(workPlanId);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (!wp.isEditable()) {
            throw ApiException.badRequest("WORK_PLAN_NOT_EDITABLE", "DRAFT 상태에서만 자원 편집 가능");
        }

        Person p = personRepo.findById(req.personId())
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원 없음"));
        if (site != null) ensureSupplierIsParticipant(site.getId(), p.getSupplierId());
        // S-12+: 인력공급사 회사 사업자등록증 VERIFIED 게이트.
        if (bpBizCertGate != null && !bpBizCertGate.isSupplierBizCertVerified(p.getSupplierId())) {
            throw ApiException.badRequest("SUPPLIER_BIZ_CERT_UNVERIFIED",
                    "인력공급사 사업자등록증이 검증되어야 작업계획서에 자원을 추가할 수 있습니다");
        }

        // 매칭 장비가 있으면 같은 plan 에 등록되어 있는지 확인.
        if (req.equipmentId() != null) {
            wpeRepo.findByWorkPlanIdAndEquipmentId(workPlanId, req.equipmentId())
                    .orElseThrow(() -> ApiException.badRequest("EQUIPMENT_NOT_IN_PLAN",
                            "매칭하려는 장비가 작업계획서에 추가되어 있지 않습니다"));
        }

        if (wppRepo.findByWorkPlanIdAndPersonId(workPlanId, p.getId()).isPresent()) {
            throw ApiException.badRequest("ALREADY_ADDED", "이미 추가된 인원입니다");
        }

        ComplianceResult compliance = evaluateCompliance(OwnerType.PERSON, p.getId(), req.override(), req.overrideReason(), actor);
        recordCompliance(wp.getId(), OwnerType.PERSON, p.getId(), compliance, actor);

        wppRepo.save(WorkPlanPerson.builder()
                .workPlanId(workPlanId)
                .personId(p.getId())
                .supplierCompanyId(p.getSupplierId())
                .equipmentId(req.equipmentId())
                .role(req.role())
                .note(req.note())
                .build());

        auditLog.record(actor, AuditAction.WORK_PLAN_PERSON_ADDED, AuditTargetType.WORK_PLAN_PERSON,
                p.getId(), p.getSupplierId(), wp.getSiteId(), null,
                "{\"work_plan_id\":" + workPlanId + ",\"compliance\":\"" + compliance.status.name() + "\"}");

        return toDetailResponse(wp);
    }

    public WorkPlanResponse removePerson(Long workPlanId, Long personId, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(workPlanId);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (!wp.isEditable()) {
            throw ApiException.badRequest("WORK_PLAN_NOT_EDITABLE", "DRAFT 상태에서만 자원 편집 가능");
        }
        WorkPlanPerson row = wppRepo.findByWorkPlanIdAndPersonId(workPlanId, personId)
                .orElseThrow(() -> ApiException.notFound("WP_PERSON_NOT_FOUND", "인원이 추가되어 있지 않습니다"));
        wppRepo.delete(row);
        auditLog.record(actor, AuditAction.WORK_PLAN_PERSON_REMOVED, AuditTargetType.WORK_PLAN_PERSON,
                personId, row.getSupplierCompanyId(), wp.getSiteId(),
                "{\"work_plan_id\":" + workPlanId + "}", null);
        return toDetailResponse(wp);
    }

    // ================== 상태 전이 ==================

    /** 누락 자원+서류 검증 endpoint 용. submit 검증과 같은 규칙. */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> listMissingDocs(Long id, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        ensureCanManage(actor, wp);
        java.util.List<WorkPlanEquipment> equipList = wpeRepo.findByWorkPlanIdOrderByIdAsc(id);
        java.util.List<WorkPlanPerson> personList = wppRepo.findByWorkPlanIdOrderByIdAsc(id);

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (WorkPlanEquipment e : equipList) {
            java.util.List<DocumentType> missing = missingBlockingTypes(OwnerType.EQUIPMENT, e.getEquipmentId());
            if (missing.isEmpty()) continue;
            String label = equipmentRepo.findById(e.getEquipmentId())
                    .map(eq -> eq.getVehicleNo() != null ? eq.getVehicleNo() : (eq.getModel() != null ? eq.getModel() : "장비#" + e.getEquipmentId()))
                    .orElse("장비#" + e.getEquipmentId());
            result.add(java.util.Map.of(
                    "owner_type", "EQUIPMENT",
                    "owner_id", e.getEquipmentId(),
                    "owner_label", label,
                    "supplier_company_id", e.getSupplierCompanyId(),
                    "missing", missing.stream().map(t -> {
                        var open = supplementRepo.findByTargetOwnerTypeAndTargetOwnerIdAndDocumentTypeIdAndStatus(
                                OwnerType.EQUIPMENT, e.getEquipmentId(), t.getId(),
                                com.skep.supplement.DocumentSupplementStatus.OPEN);
                        return java.util.Map.<String, Object>of(
                                "document_type_id", t.getId(),
                                "document_type_name", t.getName(),
                                "supplement_status", open.isEmpty() ? "NONE" : "OPEN"
                        );
                    }).toList()
            ));
        }
        for (WorkPlanPerson p : personList) {
            java.util.List<DocumentType> missing = missingBlockingTypes(OwnerType.PERSON, p.getPersonId());
            if (missing.isEmpty()) continue;
            String label = personRepo.findById(p.getPersonId())
                    .map(Person::getName)
                    .orElse("인원#" + p.getPersonId());
            result.add(java.util.Map.of(
                    "owner_type", "PERSON",
                    "owner_id", p.getPersonId(),
                    "owner_label", label,
                    "supplier_company_id", p.getSupplierCompanyId(),
                    "missing", missing.stream().map(t -> {
                        var open = supplementRepo.findByTargetOwnerTypeAndTargetOwnerIdAndDocumentTypeIdAndStatus(
                                OwnerType.PERSON, p.getPersonId(), t.getId(),
                                com.skep.supplement.DocumentSupplementStatus.OPEN);
                        return java.util.Map.<String, Object>of(
                                "document_type_id", t.getId(),
                                "document_type_name", t.getName(),
                                "supplement_status", open.isEmpty() ? "NONE" : "OPEN"
                        );
                    }).toList()
            ));
        }
        return result;
    }

    /** evaluateForClone 의 BLOCKED 검출과 동일한 로직 — 누락 DocumentType list 반환. */
    private java.util.List<DocumentType> missingBlockingTypes(OwnerType ownerType, Long ownerId) {
        java.util.List<DocumentType> blocking = docTypeRepo
                .findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc(ownerType);
        if (ownerType == OwnerType.PERSON) {
            Person p = personRepo.findById(ownerId).orElse(null);
            Set<com.skep.person.PersonRole> personRoles = p != null ? p.getRoles() : Set.of();
            // PERSON 적용여부는 역할×서류 junction(행 존재) 기준. 역할 미보유(비영속)만 기존 CSV fallback.
            java.util.Set<Long> applicableTypeIds = !personRoles.isEmpty() ? personDocReq.applicableDocTypeIds(personRoles) : null;
            blocking = blocking.stream()
                    .filter(t -> applicableTypeIds != null
                            ? applicableTypeIds.contains(t.getId())
                            : matchesPersonRoles(t.getAppliesToPersonRoles(), personRoles))
                    .toList();
        } else if (ownerType == OwnerType.EQUIPMENT) {
            com.skep.equipment.Equipment e = equipmentRepo.findById(ownerId).orElse(null);
            String cat = e != null ? e.getCategory() : null;
            // EQUIPMENT 적용여부는 종류×서류 junction(행 존재) 기준. cat==null(비영속)만 기존 CSV fallback.
            java.util.Set<Long> applicableTypeIds = cat != null ? equipDocReq.applicableDocTypeIds(cat) : null;
            blocking = blocking.stream()
                    .filter(t -> applicableTypeIds != null
                            ? applicableTypeIds.contains(t.getId())
                            : matchesEquipmentCategory(t.getAppliesToCategories(), cat))
                    .toList();
        }
        if (blocking.isEmpty()) return java.util.List.of();
        Set<Long> blockingIds = new HashSet<>();
        for (DocumentType t : blocking) blockingIds.add(t.getId());
        LocalDate today = LocalDate.now();
        Set<Long> validIds = new HashSet<>();
        for (Object[] row : docRepo.findValidVerifiedTypesByOwners(ownerType, java.util.List.of(ownerId), today)) {
            Long typeId = (Long) row[1];
            if (blockingIds.contains(typeId)) validIds.add(typeId);
        }
        return blocking.stream().filter(t -> !validIds.contains(t.getId())).toList();
    }

    public WorkPlan submit(Long id, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (wp.getStatus() != WorkPlanStatus.DRAFT) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "DRAFT 에서만 제출 가능 (현재: " + wp.getStatus() + ")");
        }
        var equipList = wpeRepo.findByWorkPlanIdOrderByIdAsc(id);
        var personList = wppRepo.findByWorkPlanIdOrderByIdAsc(id);
        if (equipList.isEmpty() && personList.isEmpty()) {
            throw ApiException.badRequest("NO_RESOURCES", "장비 또는 인원이 하나 이상 필요합니다");
        }

        // S-7.1 P1: 제출 시점 컴플라이언스 재검사 — clone 우회 + 추가 후 만료 차단.
        // 단, 자원별 가장 최근 snapshot 이 OVERRIDDEN 이면 ADMIN 이 이미 명시 승인했으므로 통과.
        Map<String, ComplianceStatus> latestPerResource = new HashMap<>();
        for (WorkPlanComplianceCheck c : wpccRepo.findByWorkPlanIdOrderByCheckedAtDesc(id)) {
            String key = c.getTargetType().name() + ":" + c.getTargetId();
            latestPerResource.putIfAbsent(key, c.getStatus());
        }

        List<String> nowBlocked = new ArrayList<>();
        for (WorkPlanEquipment e : equipList) {
            ComplianceResult r = evaluateForClone(OwnerType.EQUIPMENT, e.getEquipmentId());
            if (r.status() == ComplianceStatus.BLOCKED
                    && latestPerResource.get("EQUIPMENT:" + e.getEquipmentId()) != ComplianceStatus.OVERRIDDEN) {
                nowBlocked.add("장비#" + e.getEquipmentId() + " (" + r.reason() + ")");
            }
        }
        for (WorkPlanPerson p : personList) {
            ComplianceResult r = evaluateForClone(OwnerType.PERSON, p.getPersonId());
            if (r.status() == ComplianceStatus.BLOCKED
                    && latestPerResource.get("PERSON:" + p.getPersonId()) != ComplianceStatus.OVERRIDDEN) {
                nowBlocked.add("인원#" + p.getPersonId() + " (" + r.reason() + ")");
            }
        }
        if (!nowBlocked.isEmpty()) {
            throw ApiException.badRequest("DOCUMENTS_BLOCKED_AT_SUBMIT",
                    "제출 시점 필수 서류 미비: " + String.join("; ", nowBlocked)
                            + ". 자원을 제거하거나 ADMIN 이 강제 진행으로 재추가하세요.");
        }

        // DocReq: 제출 시점 자원 점검 요청 모두 APPROVED 검증.
        // wp 의 점검 요청이 1개 이상 있으면 모두 APPROVED 여야 함. 점검 요청 자체가 없으면 통과.
        java.util.List<com.skep.resourceCheck.ResourceCheckRequest> wpChecks =
                resourceCheckRepo.findByWorkPlanIdOrderByIdDesc(id);
        java.util.List<String> notApproved = new java.util.ArrayList<>();
        for (var c : wpChecks) {
            if (c.getStatus() != com.skep.resourceCheck.ResourceCheckStatus.APPROVED
                    && c.getStatus() != com.skep.resourceCheck.ResourceCheckStatus.CANCELLED) {
                String label = c.getOwnerType().name() + "#" + c.getOwnerId()
                        + " (" + c.getCheckType().name() + ", " + c.getStatus().name() + ")";
                notApproved.add(label);
            }
        }
        if (!notApproved.isEmpty()) {
            throw ApiException.badRequest("RESOURCE_CHECKS_INCOMPLETE",
                    "자원 점검 요청이 모두 승인되어야 제출할 수 있습니다: " + String.join("; ", notApproved));
        }

        // S-12: 제출 시점 5개 사인 모두 SIGNED 검증.
        if (signatureService != null && !signatureService.allSigned(id)) {
            throw ApiException.badRequest("SIGNATURES_INCOMPLETE",
                    "제출 전에 5개 사인 (작성자/담당자/확인자/검토자/승인자) 모두 완료되어야 합니다");
        }

        wp.submit(actor.id());
        auditLog.record(actor, AuditAction.WORK_PLAN_SUBMITTED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(),
                "{\"status\":\"DRAFT\"}", "{\"status\":\"SUBMITTED\"}");
        return wp;
    }

    public WorkPlan approve(Long id, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        // 승인은 ADMIN 또는 BP (자기 회사) 가능. 초기 단순화 정책.
        ensureCanManage(actor, wp);
        if (wp.getStatus() != WorkPlanStatus.SUBMITTED) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "SUBMITTED 에서만 승인 가능 (현재: " + wp.getStatus() + ")");
        }
        wp.approve(actor.id());
        auditLog.record(actor, AuditAction.WORK_PLAN_APPROVED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(),
                "{\"status\":\"SUBMITTED\"}", "{\"status\":\"APPROVED\"}");
        return wp;
    }

    /**
     * APPROVED → IN_PROGRESS. 작업 시작 + 자원을 plan 사이트로 자동 배치.
     *
     * 동기화 정책 (S-8.1, S-8.5 강화):
     * - 자원이 미배치면 plan 사이트로 신규 배치 (triggered_by_work_plan_id = wp.id 기록).
     * - 이미 plan 사이트에 배치되어 있으면 no-op.
     * - 다른 사이트에 배치되어 있으면 conflict — 기본 차단 (RESOURCE_CONFLICTS 400).
     *   ADMIN 만 force=true + forceReason 로 우회 가능. force 시 자원은 그 자리에 두고 plan 만 IN_PROGRESS,
     *   audit WORK_PLAN_FORCE_STARTED + WORK_PLAN_RESOURCE_CONFLICT 양쪽에 사유 기록.
     */
    public WorkPlan start(Long id, StartWorkPlanRequest req, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (wp.getStatus() != WorkPlanStatus.APPROVED) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "APPROVED 에서만 작업 시작 가능 (현재: " + wp.getStatus() + ")");
        }

        boolean force = req != null && Boolean.TRUE.equals(req.force());
        String forceReason = req != null ? req.forceReason() : null;

        // G-1: 자원별 안전점검 완료 검증 — 미완료 자원이 있으면 차단 (force=true 면 ADMIN 만 우회).
        List<String> safetyMisses = new ArrayList<>();
        for (WorkPlanEquipment row : wpeRepo.findByWorkPlanIdOrderByIdAsc(id)) {
            boolean done = safetyInspectionRepo
                    .findBySiteIdAndTargetTypeAndTargetId(wp.getSiteId(),
                            com.skep.safety.InspectionTarget.VEHICLE, row.getEquipmentId())
                    .stream().anyMatch(s -> s.getStatus() == com.skep.safety.InspectionStatus.COMPLETED);
            if (!done) safetyMisses.add("장비#" + row.getEquipmentId());
        }
        for (WorkPlanPerson row : wppRepo.findByWorkPlanIdOrderByIdAsc(id)) {
            boolean done = safetyInspectionRepo
                    .findBySiteIdAndTargetTypeAndTargetId(wp.getSiteId(),
                            com.skep.safety.InspectionTarget.PERSON, row.getPersonId())
                    .stream().anyMatch(s -> s.getStatus() == com.skep.safety.InspectionStatus.COMPLETED);
            if (!done) safetyMisses.add("인원#" + row.getPersonId());
        }
        if (!safetyMisses.isEmpty() && !force) {
            throw ApiException.badRequest("SAFETY_INSPECTION_INCOMPLETE",
                    "안전점검 미완료 자원: " + String.join(", ", safetyMisses)
                            + ". 안전점검을 완료한 후 시작할 수 있습니다.");
        }

        // Compl-5: 이행지시(ComplianceOrder) 미승인/만료 자원 차단.
        List<String> complianceMisses = new ArrayList<>();
        var eqIds = wpeRepo.findByWorkPlanIdOrderByIdAsc(id).stream()
                .map(WorkPlanEquipment::getEquipmentId).toList();
        var pIds = wppRepo.findByWorkPlanIdOrderByIdAsc(id).stream()
                .map(WorkPlanPerson::getPersonId).toList();
        if (complianceOrderService != null) {
            for (var blk : complianceOrderService.findBlockingFor(
                    com.skep.compliance.ComplianceTargetType.VEHICLE, eqIds)) {
                complianceMisses.add("장비#" + blk.getTargetId() + "(" + blk.getOrderType() + ")");
            }
            for (var blk : complianceOrderService.findBlockingFor(
                    com.skep.compliance.ComplianceTargetType.PERSON, pIds)) {
                complianceMisses.add("인원#" + blk.getTargetId() + "(" + blk.getOrderType() + ")");
            }
        }
        if (!complianceMisses.isEmpty() && !force) {
            throw ApiException.badRequest("COMPLIANCE_ORDER_PENDING",
                    "미이행/미승인 이행지시: " + String.join(", ", complianceMisses)
                            + ". 증빙 승인 후 시작할 수 있습니다.");
        }

        // 1) 사전 conflict 검사 (mutation 전).
        List<String> conflicts = new ArrayList<>();
        for (WorkPlanEquipment row : wpeRepo.findByWorkPlanIdOrderByIdAsc(id)) {
            var active = eqAssignments.findByEquipmentIdAndReleasedAtIsNull(row.getEquipmentId());
            if (active.isPresent() && !active.get().getSiteId().equals(wp.getSiteId())) {
                conflicts.add("장비#" + row.getEquipmentId() + " (현재 site#" + active.get().getSiteId() + ")");
            }
        }
        for (WorkPlanPerson row : wppRepo.findByWorkPlanIdOrderByIdAsc(id)) {
            var active = personAssignments.findByPersonIdAndReleasedAtIsNull(row.getPersonId());
            if (active.isPresent() && !active.get().getSiteId().equals(wp.getSiteId())) {
                conflicts.add("인원#" + row.getPersonId() + " (현재 site#" + active.get().getSiteId() + ")");
            }
        }
        if (!conflicts.isEmpty()) {
            if (!force) {
                throw ApiException.badRequest("RESOURCE_CONFLICTS",
                        "다른 현장에 배치된 자원이 있습니다: " + String.join("; ", conflicts)
                                + ". ADMIN 이 force=true + force_reason 로 강제 시작할 수 있습니다.");
            }
            if (actor.role() != Role.ADMIN) {
                throw ApiException.forbidden("FORCE_ADMIN_ONLY", "강제 시작은 ADMIN 만 가능합니다");
            }
            if (forceReason == null || forceReason.isBlank()) {
                throw ApiException.badRequest("FORCE_REASON_REQUIRED", "강제 시작 사유는 필수입니다");
            }
        }

        // 2) 자원 동기화 (conflict 자원은 그대로 두고 audit 만, 미배치 자원은 plan 사이트로 자동 배치).
        SyncReport rep = syncAssignmentsOnStart(wp, actor);

        // 3) 상태 전이.
        wp.start();
        auditLog.record(actor, AuditAction.WORK_PLAN_STARTED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(),
                "{\"status\":\"APPROVED\"}",
                "{\"status\":\"IN_PROGRESS\""
                        + ",\"equipment_assigned\":" + rep.equipAssigned
                        + ",\"equipment_already_here\":" + rep.equipAlreadyHere
                        + ",\"equipment_conflict\":" + rep.equipConflict
                        + ",\"person_assigned\":" + rep.personAssigned
                        + ",\"person_already_here\":" + rep.personAlreadyHere
                        + ",\"person_conflict\":" + rep.personConflict
                        + (force ? ",\"forced\":true,\"force_reason\":\"" + escape(forceReason) + "\"" : "")
                        + "}");
        if (force) {
            auditLog.record(actor, AuditAction.WORK_PLAN_FORCE_STARTED, AuditTargetType.WORK_PLAN,
                    wp.getId(), wp.getBpCompanyId(), wp.getSiteId(), null,
                    "{\"force_reason\":\"" + escape(forceReason) + "\""
                            + ",\"conflict_count\":" + conflicts.size() + "}");
        }
        return wp;
    }

    private record SyncReport(int equipAssigned, int equipAlreadyHere, int equipConflict,
                              int personAssigned, int personAlreadyHere, int personConflict) {}

    private SyncReport syncAssignmentsOnStart(WorkPlan wp, AuthenticatedUser actor) {
        Long siteId = wp.getSiteId();
        if (siteId == null) {
            // 현장 미정 작업계획서는 자원 사이트 배치 동기화 skip.
            return new SyncReport(0, 0, 0, 0, 0, 0);
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int eqA = 0, eqH = 0, eqC = 0;
        int pA = 0, pH = 0, pC = 0;

        for (WorkPlanEquipment row : wpeRepo.findByWorkPlanIdOrderByIdAsc(wp.getId())) {
            Equipment e = equipmentRepo.findById(row.getEquipmentId()).orElse(null);
            if (e == null) continue;
            var active = eqAssignments.findByEquipmentIdAndReleasedAtIsNull(e.getId());
            if (active.isPresent()) {
                if (active.get().getSiteId().equals(siteId)) {
                    eqH++;
                } else {
                    eqC++;
                    auditLog.record(actor, AuditAction.WORK_PLAN_RESOURCE_CONFLICT,
                            AuditTargetType.EQUIPMENT, e.getId(), e.getSupplierId(), siteId, null,
                            "{\"work_plan_id\":" + wp.getId()
                                    + ",\"current_site_id\":" + active.get().getSiteId()
                                    + ",\"target_site_id\":" + siteId
                                    + ",\"resolution\":\"force_proceeded\"}");
                }
                continue;
            }
            eqAssignments.save(EquipmentAssignment.builder()
                    .equipmentId(e.getId())
                    .siteId(siteId)
                    .assignedAt(now)
                    .assignedBy(actor.id())
                    .note("auto on work plan #" + wp.getId() + " start")
                    .triggeredByWorkPlanId(wp.getId())
                    .build());
            e.assignToSite(siteId, now);
            auditLog.record(actor, AuditAction.EQUIPMENT_ASSIGNED, AuditTargetType.EQUIPMENT,
                    e.getId(), e.getSupplierId(), siteId, null,
                    "{\"site_id\":" + siteId + ",\"status\":\"ASSIGNED\""
                            + ",\"trigger\":\"work_plan_start\",\"work_plan_id\":" + wp.getId() + "}");
            eqA++;
        }

        for (WorkPlanPerson row : wppRepo.findByWorkPlanIdOrderByIdAsc(wp.getId())) {
            Person p = personRepo.findById(row.getPersonId()).orElse(null);
            if (p == null) continue;
            var active = personAssignments.findByPersonIdAndReleasedAtIsNull(p.getId());
            if (active.isPresent()) {
                if (active.get().getSiteId().equals(siteId)) {
                    pH++;
                } else {
                    pC++;
                    auditLog.record(actor, AuditAction.WORK_PLAN_RESOURCE_CONFLICT,
                            AuditTargetType.PERSON, p.getId(), p.getSupplierId(), siteId, null,
                            "{\"work_plan_id\":" + wp.getId()
                                    + ",\"current_site_id\":" + active.get().getSiteId()
                                    + ",\"target_site_id\":" + siteId
                                    + ",\"resolution\":\"force_proceeded\"}");
                }
                continue;
            }
            personAssignments.save(PersonAssignment.builder()
                    .personId(p.getId())
                    .siteId(siteId)
                    .assignedAt(now)
                    .assignedBy(actor.id())
                    .note("auto on work plan #" + wp.getId() + " start")
                    .triggeredByWorkPlanId(wp.getId())
                    .build());
            p.assignToSite(siteId, now);
            auditLog.record(actor, AuditAction.PERSON_ASSIGNED, AuditTargetType.PERSON,
                    p.getId(), p.getSupplierId(), siteId, null,
                    "{\"site_id\":" + siteId + ",\"status\":\"ON_DUTY\""
                            + ",\"trigger\":\"work_plan_start\",\"work_plan_id\":" + wp.getId() + "}");
            pA++;
        }

        return new SyncReport(eqA, eqH, eqC, pA, pH, pC);
    }

    /**
     * IN_PROGRESS → DONE. 작업 완료 + 이 plan 이 자동 생성한 배치들 자동 해제 (S-8.5).
     * 수동으로 만든 배치 (triggered_by_work_plan_id IS NULL) 는 건드리지 않음.
     */
    public WorkPlan complete(Long id, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (wp.getStatus() != WorkPlanStatus.IN_PROGRESS) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "IN_PROGRESS 에서만 완료 처리 가능 (현재: " + wp.getStatus() + ")");
        }
        ReleaseReport rep = releaseAutoAssignments(wp, actor, "work_plan_complete");
        wp.complete();
        auditLog.record(actor, AuditAction.WORK_PLAN_COMPLETED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(),
                "{\"status\":\"IN_PROGRESS\"}",
                "{\"status\":\"DONE\""
                        + ",\"equipment_released\":" + rep.equipReleased
                        + ",\"person_released\":" + rep.personReleased + "}");
        return wp;
    }

    /**
     * 작업계획서 취소. CANCELLED/DONE 외에서는 가능. IN_PROGRESS 였다면 자동 생성된 배치도 자동 해제.
     */
    public WorkPlan cancel(Long id, CancelRequest req, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(id);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (wp.getStatus() == WorkPlanStatus.CANCELLED || wp.getStatus() == WorkPlanStatus.DONE) {
            throw ApiException.badRequest("INVALID_TRANSITION",
                    "현재 상태에서 취소할 수 없습니다: " + wp.getStatus());
        }
        WorkPlanStatus before = wp.getStatus();
        // IN_PROGRESS 였다면 자동 배치들도 해제 (다른 상태에서는 자동 배치가 없음).
        ReleaseReport rep = before == WorkPlanStatus.IN_PROGRESS
                ? releaseAutoAssignments(wp, actor, "work_plan_cancel")
                : new ReleaseReport(0, 0);
        wp.cancel(actor.id(), req.reason());
        auditLog.record(actor, AuditAction.WORK_PLAN_CANCELLED, AuditTargetType.WORK_PLAN,
                wp.getId(), wp.getBpCompanyId(), wp.getSiteId(),
                "{\"status\":\"" + before.name() + "\"}",
                "{\"status\":\"CANCELLED\",\"reason\":\"" + escape(req.reason()) + "\""
                        + ",\"equipment_released\":" + rep.equipReleased
                        + ",\"person_released\":" + rep.personReleased + "}");
        return wp;
    }

    private record ReleaseReport(int equipReleased, int personReleased) {}

    /**
     * 이 plan 의 start() 가 자동 생성한 활성 배치(triggered_by_work_plan_id = wp.id)를 해제.
     * 자원의 currentSiteId 도 갱신. 수동 배치(triggered_by NULL)는 건드리지 않음.
     */
    private ReleaseReport releaseAutoAssignments(WorkPlan wp, AuthenticatedUser actor, String reason) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int eqR = 0, pR = 0;

        for (EquipmentAssignment a : eqAssignments.findByTriggeredByWorkPlanIdAndReleasedAtIsNull(wp.getId())) {
            a.release(now, actor.id(), reason);
            Equipment e = equipmentRepo.findById(a.getEquipmentId()).orElse(null);
            if (e != null && a.getSiteId().equals(e.getCurrentSiteId())) {
                e.releaseFromSite();
            }
            auditLog.record(actor, AuditAction.EQUIPMENT_UNASSIGNED, AuditTargetType.EQUIPMENT,
                    a.getEquipmentId(), e != null ? e.getSupplierId() : null, a.getSiteId(),
                    "{\"site_id\":" + a.getSiteId() + ",\"status\":\"ASSIGNED\"}",
                    "{\"trigger\":\"" + reason + "\",\"work_plan_id\":" + wp.getId() + "}");
            eqR++;
        }
        for (PersonAssignment a : personAssignments.findByTriggeredByWorkPlanIdAndReleasedAtIsNull(wp.getId())) {
            a.release(now, actor.id(), reason);
            Person p = personRepo.findById(a.getPersonId()).orElse(null);
            if (p != null && a.getSiteId().equals(p.getCurrentSiteId())) {
                p.releaseFromSite();
            }
            auditLog.record(actor, AuditAction.PERSON_UNASSIGNED, AuditTargetType.PERSON,
                    a.getPersonId(), p != null ? p.getSupplierId() : null, a.getSiteId(),
                    "{\"site_id\":" + a.getSiteId() + ",\"status\":\"ON_DUTY\"}",
                    "{\"trigger\":\"" + reason + "\",\"work_plan_id\":" + wp.getId() + "}");
            pR++;
        }
        return new ReleaseReport(eqR, pR);
    }

    // ================== 컴플라이언스 검사 ==================

    private record ComplianceResult(ComplianceStatus status, String reason, List<String> missing) {}

    /**
     * 서류 컴플라이언스 평가:
     *  - blocks_assignment 필수 type 중 chain head 가 verified+안만료 가 아니면 BLOCKED
     *  - BLOCKED + override(ADMIN+사유) → OVERRIDDEN 으로 통과
     *  - 누락 없음 + 만료 임박 (≤30d) 있음 → WARNING
     *  - 그 외 → OK
     */
    private ComplianceResult evaluateCompliance(OwnerType ownerType, Long ownerId,
                                                Boolean override, String overrideReason,
                                                AuthenticatedUser actor) {
        List<DocumentType> blocking = docTypeRepo
                .findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc(ownerType);
        // 인원: 역할 한정 서류는 해당 역할자에게만 요구 (운전면허증은 OPERATOR 만 등).
        // 장비: 카테고리 한정 서류는 해당 카테고리에만 요구.
        if (ownerType == OwnerType.PERSON) {
            Person p = personRepo.findById(ownerId).orElse(null);
            Set<com.skep.person.PersonRole> personRoles = p != null ? p.getRoles() : Set.of();
            // PERSON 적용여부는 역할×서류 junction(행 존재) 기준. 역할 미보유(비영속)만 기존 CSV fallback.
            java.util.Set<Long> applicableTypeIds = !personRoles.isEmpty() ? personDocReq.applicableDocTypeIds(personRoles) : null;
            blocking = blocking.stream()
                    .filter(t -> applicableTypeIds != null
                            ? applicableTypeIds.contains(t.getId())
                            : matchesPersonRoles(t.getAppliesToPersonRoles(), personRoles))
                    .toList();
        } else if (ownerType == OwnerType.EQUIPMENT) {
            com.skep.equipment.Equipment e = equipmentRepo.findById(ownerId).orElse(null);
            String cat = e != null ? e.getCategory() : null;
            // EQUIPMENT 적용여부는 종류×서류 junction(행 존재) 기준. cat==null(비영속)만 기존 CSV fallback.
            java.util.Set<Long> applicableTypeIds = cat != null ? equipDocReq.applicableDocTypeIds(cat) : null;
            blocking = blocking.stream()
                    .filter(t -> applicableTypeIds != null
                            ? applicableTypeIds.contains(t.getId())
                            : matchesEquipmentCategory(t.getAppliesToCategories(), cat))
                    .toList();
        }
        if (blocking.isEmpty()) {
            return new ComplianceResult(ComplianceStatus.OK, null, List.of());
        }
        Set<Long> blockingIds = new HashSet<>();
        Map<Long, String> nameByTypeId = new HashMap<>();
        for (DocumentType t : blocking) { blockingIds.add(t.getId()); nameByTypeId.put(t.getId(), t.getName()); }

        LocalDate today = LocalDate.now();
        Set<Long> validIds = new HashSet<>();
        for (Object[] row : docRepo.findValidVerifiedTypesByOwners(ownerType, List.of(ownerId), today)) {
            Long typeId = (Long) row[1];
            if (blockingIds.contains(typeId)) validIds.add(typeId);
        }
        List<String> missing = blocking.stream()
                .filter(t -> !validIds.contains(t.getId()))
                .map(DocumentType::getName)
                .toList();

        if (!missing.isEmpty()) {
            // BLOCKED — override 검사.
            if (Boolean.TRUE.equals(override)) {
                if (actor.role() != Role.ADMIN) {
                    throw ApiException.forbidden("OVERRIDE_ADMIN_ONLY",
                            "서류 미비 강제 진행은 관리자만 가능합니다");
                }
                if (overrideReason == null || overrideReason.isBlank()) {
                    throw ApiException.badRequest("OVERRIDE_REASON_REQUIRED",
                            "강제 진행 시 사유는 필수입니다");
                }
                return new ComplianceResult(ComplianceStatus.OVERRIDDEN,
                        "필수 서류 누락 강제 진행: " + String.join(", ", missing), missing);
            }
            throw ApiException.badRequest("DOCUMENTS_BLOCKED",
                    "필수 서류 누락/만료/반려: " + String.join(", ", missing));
        }

        // 만료 임박 검사 (chain head 만, blocks_assignment 한정으로 단순화).
        LocalDate maxDate = today.plusDays(EXPIRING_DAYS);
        long expiringCount = docRepo.findRiskyForOwners(ownerType, List.of(ownerId), maxDate).size();
        if (expiringCount > 0) {
            return new ComplianceResult(ComplianceStatus.WARNING,
                    "만료 임박 또는 검토 필요 서류 " + expiringCount + "건", List.of());
        }
        return new ComplianceResult(ComplianceStatus.OK, null, List.of());
    }

    /** DocumentType.appliesToPersonRoles (CSV, null=모든역할) ↔ person 의 roles 매칭. */
    private static boolean matchesPersonRoles(String csv, Set<com.skep.person.PersonRole> personRoles) {
        if (csv == null || csv.isBlank()) return true;
        if (personRoles == null || personRoles.isEmpty()) return false;
        for (String s : csv.split(",")) {
            String trimmed = s.trim();
            for (com.skep.person.PersonRole r : personRoles) {
                if (r.name().equalsIgnoreCase(trimmed)) return true;
            }
        }
        return false;
    }

    /** DocumentType.appliesToCategories (CSV, null=모든카테고리) ↔ equipment.category 매칭. */
    private static boolean matchesEquipmentCategory(String csv, String cat) {
        if (csv == null || csv.isBlank()) return true;
        if (cat == null) return false;
        for (String s : csv.split(",")) {
            if (s.trim().equalsIgnoreCase(cat)) return true;
        }
        return false;
    }

    private void recordCompliance(Long workPlanId, OwnerType targetType, Long targetId,
                                  ComplianceResult result, AuthenticatedUser actor) {
        wpccRepo.save(WorkPlanComplianceCheck.builder()
                .workPlanId(workPlanId)
                .targetType(targetType)
                .targetId(targetId)
                .status(result.status())
                .reason(result.reason())
                .overrideBy(result.status() == ComplianceStatus.OVERRIDDEN ? actor.id() : null)
                .overrideReason(result.status() == ComplianceStatus.OVERRIDDEN ? result.reason() : null)
                .build());
    }

    // ================== 후보 조회 (사이트의 ACTIVE 참여 공급사 자원만) ==================

    /**
     * 작업계획서에 추가 가능한 장비 후보. 사이트의 ACTIVE EQUIPMENT_SUPPLIER 참여 공급사가 보유한 장비만.
     * BP/ADMIN 권한 필요. 프론트는 이 응답만 사용해야 함 (전체 /api/equipment 노출 우회 차단).
     */
    @Transactional(readOnly = true)
    public List<EquipmentResponse> equipmentCandidates(Long workPlanId, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(workPlanId);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (site == null) return List.of();
        List<Long> supplierIds = participants.findBySiteIdOrderByIdDesc(site.getId()).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE
                        && p.getParticipantType() == SiteParticipantType.EQUIPMENT_SUPPLIER)
                .map(SiteParticipant::getCompanyId)
                .toList();
        if (supplierIds.isEmpty()) return List.of();
        return equipmentRepo.findBySupplierIdInOrderByIdDesc(supplierIds).stream()
                .map(EquipmentResponse::from)
                .toList();
    }

    /** 작업계획서에 추가 가능한 인원 후보. 사이트의 ACTIVE MANPOWER_SUPPLIER 참여 공급사 소속 인원만. */
    @Transactional(readOnly = true)
    public List<PersonResponse> personCandidates(Long workPlanId, AuthenticatedUser actor) {
        WorkPlan wp = getOrThrow(workPlanId);
        Site site = siteOrThrow(wp.getSiteId());
        ensureCanManage(actor, wp);
        if (site == null) return List.of();
        List<Long> supplierIds = participants.findBySiteIdOrderByIdDesc(site.getId()).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE
                        && p.getParticipantType() == SiteParticipantType.MANPOWER_SUPPLIER)
                .map(SiteParticipant::getCompanyId)
                .toList();
        if (supplierIds.isEmpty()) return List.of();
        return personRepo.findBySupplierIdInOrderByIdDesc(supplierIds).stream()
                .map(PersonResponse::from)
                .toList();
    }

    // ================== 상세 응답 ==================

    @Transactional(readOnly = true)
    protected WorkPlanResponse toDetailResponse(WorkPlan wp) {
        Site site = sites.findById(wp.getSiteId()).orElse(null);
        Company bp = companies.findById(wp.getBpCompanyId()).orElse(null);

        // equipment / person 목록 + 메타
        List<WorkPlanEquipment> wpeList = wpeRepo.findByWorkPlanIdOrderByIdAsc(wp.getId());
        List<WorkPlanPerson> wppList = wppRepo.findByWorkPlanIdOrderByIdAsc(wp.getId());
        List<WorkPlanComplianceCheck> ccList = wpccRepo.findByWorkPlanIdOrderByCheckedAtDesc(wp.getId());

        Map<Long, Equipment> equipMap = mapById(
                equipmentRepo.findAllById(wpeList.stream().map(WorkPlanEquipment::getEquipmentId).toList()),
                Equipment::getId);
        Map<Long, Person> personMap = mapById(
                personRepo.findAllById(wppList.stream().map(WorkPlanPerson::getPersonId).toList()),
                Person::getId);
        Set<Long> companyIds = new HashSet<>();
        wpeList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        wppList.forEach(x -> companyIds.add(x.getSupplierCompanyId()));
        Map<Long, Company> companyMap = mapById(companies.findAllById(companyIds), Company::getId);

        var equipmentResp = wpeList.stream().map(wpe -> {
            Equipment e = equipMap.get(wpe.getEquipmentId());
            String name = e != null ? Optional.ofNullable(e.getModel())
                    .orElse(Optional.ofNullable(e.getVehicleNo()).orElse("(이름없음)")) : "(삭제됨)";
            Company c = companyMap.get(wpe.getSupplierCompanyId());
            return WorkPlanEquipmentResponse.from(wpe, name,
                    e != null ? e.getCategory() : null,
                    c != null ? c.getName() : null);
        }).toList();

        var personResp = wppList.stream().map(wpp -> {
            Person p = personMap.get(wpp.getPersonId());
            Company c = companyMap.get(wpp.getSupplierCompanyId());
            return WorkPlanPersonResponse.from(wpp,
                    p != null ? p.getName() : "(삭제됨)",
                    c != null ? c.getName() : null);
        }).toList();

        var complianceResp = ccList.stream().map(ComplianceCheckResponse::from).toList();

        return WorkPlanResponse.detail(
                wp,
                site != null ? site.getName() : null,
                bp != null ? bp.getName() : null,
                equipmentResp, personResp, complianceResp
        );
    }

    // ================== 헬퍼 ==================

    private WorkPlan getOrThrow(Long id) {
        return wpRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서를 찾을 수 없습니다"));
    }

    /** site_id 가 null 이면 null 리턴 (현장 미정 작업계획서). 있으면 fetch — 없으면 404. */
    private Site siteOrThrow(Long siteId) {
        if (siteId == null) return null;
        return sites.findById(siteId).orElseThrow(() ->
                ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
    }

    private void ensureCanManage(AuthenticatedUser actor, Site site) {
        ensureCanManageBp(actor, site != null ? site.getBpCompanyId() : null);
    }

    /** WorkPlan 기반 권한 검사 — site 가 null 이어도 wp.bpCompanyId 로 검증. */
    private void ensureCanManage(AuthenticatedUser actor, WorkPlan wp) {
        ensureCanManageBp(actor, wp.getBpCompanyId());
    }

    /** site 없이도 BP 본인/ADMIN 검증. */
    private void ensureCanManageBp(AuthenticatedUser actor, Long bpCompanyId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            requireCompany(actor);
            if (bpCompanyId != null && bpCompanyId.equals(actor.companyId())) return;
        }
        throw ApiException.forbidden("WORK_PLAN_DENIED", "작업계획서 관리 권한이 없습니다");
    }

    private void ensureCanView(AuthenticatedUser actor, WorkPlan wp) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP && actor.companyId() != null
                && wp.getBpCompanyId().equals(actor.companyId())) return;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && actor.companyId() != null) {
            // 자기 회사 자원이 포함된 경우만.
            boolean inEquip = wpeRepo.findByWorkPlanIdOrderByIdAsc(wp.getId()).stream()
                    .anyMatch(x -> actor.companyId().equals(x.getSupplierCompanyId()));
            boolean inPerson = wppRepo.findByWorkPlanIdOrderByIdAsc(wp.getId()).stream()
                    .anyMatch(x -> actor.companyId().equals(x.getSupplierCompanyId()));
            if (inEquip || inPerson) return;
        }
        throw ApiException.forbidden("WORK_PLAN_VIEW_DENIED", "이 작업계획서에 접근할 수 없습니다");
    }

    private void ensureSupplierIsParticipant(Long siteId, Long supplierId) {
        // BP 본인 회사가 보유한 자원 (#5 직속 운전수) 은 participant 가 아니어도 자기 사이트에 직접 추가 가능.
        Site site = sites.findById(siteId).orElse(null);
        if (site != null && site.getBpCompanyId() != null && site.getBpCompanyId().equals(supplierId)) {
            return;
        }
        boolean ok = participants.existsBySiteIdAndCompanyIdAndStatus(siteId, supplierId, SiteParticipantStatus.ACTIVE);
        if (!ok) {
            throw ApiException.badRequest("SUPPLIER_NOT_PARTICIPANT",
                    "자원의 공급사가 현장 참여업체가 아닙니다");
        }
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
        }
    }

    private static <T> Map<Long, T> mapById(Iterable<T> items, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>();
        items.forEach(t -> m.put(idFn.apply(t), t));
        return m;
    }

    private static String escape(String s) {
        return com.skep.common.SafeText.escapeJson(s);
    }

    /** 대시보드용 — 현재 사용자가 볼 수 있는 today/upcoming 작업계획서. */
    @Transactional(readOnly = true)
    public List<WorkPlan> upcomingForActor(AuthenticatedUser actor, LocalDate from, LocalDate to) {
        if (actor.role() == Role.ADMIN) {
            return wpRepo.findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(from, to);
        }
        if (actor.role() == Role.BP && actor.companyId() != null) {
            return wpRepo.findByBpCompanyIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                    actor.companyId(), from, to);
        }
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && actor.companyId() != null) {
            return wpRepo.findUpcomingForSupplier(actor.companyId(), from, to);
        }
        return List.of();
    }
}
