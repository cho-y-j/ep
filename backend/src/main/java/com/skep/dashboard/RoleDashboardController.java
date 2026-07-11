package com.skep.dashboard;

import com.skep.assignment.EquipmentAssignmentRepository;
import com.skep.assignment.PersonAssignmentRepository;
import com.skep.audit.AuditLogService;
import com.skep.audit.dto.AuditLogResponse;
import com.skep.common.ApiException;
import com.skep.company.CompanyRepository;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.signature.SignatureStatus;
import com.skep.signature.WorksheetSignatureRepository;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.user.UserRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 역할별 대시보드 summary API.
 *
 * 기존 단일 /api/dashboard/summary 와 별개로, 역할별 endpoint 를 분리해
 * 응답 스키마와 권한 정책을 분명히 한다.
 *
 * Phase S-3 에서는 카운트와 즉시 사용 가능한 데이터만 제공한다.
 * 알림/공지/작업계획서/서류 위험 상세는 별도 도메인이 필요해 빈 배열 + TODO 로 둔다.
 */
@RestController
@RequestMapping("/api/dashboards")
public class RoleDashboardController {

    private final UserRepository users;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final EquipmentRepository equipment;
    private final PersonRepository persons;
    private final DocumentRepository documents;
    private final DocumentTypeRepository documentTypes;
    private final EquipmentAssignmentRepository eqAssignments;
    private final PersonAssignmentRepository personAssignments;
    private final AuditLogService auditLog;
    private final WorkPlanRepository workPlans;
    private final WorkPlanEquipmentRepository wpe;
    private final WorkPlanPersonRepository wpp;
    private final WorksheetSignatureRepository signatures;
    private final BpSitePipelineService bpSitePipeline;

    public RoleDashboardController(UserRepository users, CompanyRepository companies,
                                   SiteRepository sites, SiteParticipantRepository participants,
                                   EquipmentRepository equipment, PersonRepository persons,
                                   DocumentRepository documents, DocumentTypeRepository documentTypes,
                                   EquipmentAssignmentRepository eqAssignments,
                                   PersonAssignmentRepository personAssignments,
                                   AuditLogService auditLog,
                                   WorkPlanRepository workPlans,
                                   WorkPlanEquipmentRepository wpe,
                                   WorkPlanPersonRepository wpp,
                                   WorksheetSignatureRepository signatures,
                                   BpSitePipelineService bpSitePipeline) {
        this.users = users;
        this.companies = companies;
        this.sites = sites;
        this.participants = participants;
        this.equipment = equipment;
        this.persons = persons;
        this.documents = documents;
        this.documentTypes = documentTypes;
        this.eqAssignments = eqAssignments;
        this.personAssignments = personAssignments;
        this.auditLog = auditLog;
        this.workPlans = workPlans;
        this.wpe = wpe;
        this.wpp = wpp;
        this.signatures = signatures;
        this.bpSitePipeline = bpSitePipeline;
    }

    @GetMapping("/admin/summary")
    public Map<String, Object> adminSummary(@CurrentUser AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN) throw ApiException.forbidden("ADMIN_ONLY", "ADMIN 전용");
        LocalDate today = LocalDate.now();
        Map<String, Object> body = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        counts.put("companies", companies.count());
        counts.put("sites", sites.count());
        counts.put("equipment", equipment.count());
        counts.put("persons", persons.count());
        counts.put("documents_expiring30d", documents.countExpiringByDate(today.plusDays(30)));
        counts.put("users_pending", users.countByEnabled(false));
        // S-6: 작업계획서 카운트 — 오늘 + 향후 7일.
        var upcoming = workPlans.findByWorkDateBetweenOrderByWorkDateAscStartTimeAsc(today, today.plusDays(7));
        counts.put("work_plans_upcoming", (long) upcoming.size());
        body.put("counts", counts);
        body.put("recent_audit_logs", auditLog.recent(actor, 10).stream()
                .map(r -> AuditLogResponse.from(r.log())).toList());
        body.put("today_work_plans", toWorkPlanItems(upcoming, 20));
        body.put("recent_notifications", List.of());
        return body;
    }

    @GetMapping("/bp/summary")
    public Map<String, Object> bpSummary(@CurrentUser AuthenticatedUser actor) {
        if (actor.role() != Role.BP) throw ApiException.forbidden("BP_ONLY", "BP 전용");
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        // 통계 카드만 회사 관리자 전용. 사이트/감사로그/작업계획서/서류위험은 모든 직원 노출.

        List<Site> mySites = sites.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        List<Long> siteIds = mySites.stream().map(Site::getId).toList();

        Map<String, Object> body = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        if (actor.isCompanyAdmin()) {
            counts.put("my_sites", (long) mySites.size());
            counts.put("active_participants", siteIds.isEmpty() ? 0L : participants.findBySiteIdIn(siteIds).stream()
                    .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE).count());
            counts.put("equipment_on_my_sites", siteIds.stream()
                    .mapToLong(id -> eqAssignments.findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(id).size())
                    .sum());
            counts.put("persons_on_my_sites", siteIds.stream()
                    .mapToLong(id -> personAssignments.findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(id).size())
                    .sum());
        }
        body.put("counts", counts);
        body.put("is_company_admin", actor.isCompanyAdmin());

        body.put("sites", mySites.stream().map(s -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", s.getId());
            r.put("name", s.getName());
            r.put("status", s.getStatus().name());
            r.put("participant_count", participants.findBySiteIdOrderByIdDesc(s.getId()).stream()
                    .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE).count());
            r.put("equipment_count", eqAssignments.findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(s.getId()).size());
            r.put("person_count", personAssignments.findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(s.getId()).size());
            return r;
        }).toList());

        body.put("recent_audit_logs", auditLog.recent(actor, 10).stream()
                .map(r -> AuditLogResponse.from(r.log())).toList());
        // S-6: BP 자기 회사의 오늘 + 향후 7일 작업계획서.
        LocalDate today = LocalDate.now();
        var bpUpcoming = workPlans.findByBpCompanyIdAndWorkDateBetweenOrderByWorkDateAscStartTimeAsc(
                actor.companyId(), today, today.plusDays(7));
        counts.put("work_plans_upcoming", (long) bpUpcoming.size());
        body.put("today_work_plans", toWorkPlanItems(bpUpcoming, 20));
        // V14: BP 자기 사이트 ACTIVE 참여 공급사의 자원 owner_id 모아 위험 서류 조회.
        body.put("document_risks", bpDocumentRisks(siteIds));
        return body;
    }

    /** BP 의 자기 사이트들에 ACTIVE 참여 중인 공급사 → 그 회사 자원 owner_id → 위험 서류. */
    private List<Map<String, Object>> bpDocumentRisks(List<Long> mySiteIds) {
        if (mySiteIds.isEmpty()) return List.of();
        // 1) ACTIVE 참여 공급사 회사 ID 모음
        List<Long> supplierIds = participants.findBySiteIdIn(mySiteIds).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                .map(SiteParticipant::getCompanyId)
                .distinct()
                .toList();
        if (supplierIds.isEmpty()) return List.of();
        // 2) 각 자원 owner_id 모음
        List<Long> equipIds = equipment.findBySupplierIdInOrderByIdDesc(supplierIds).stream()
                .map(e -> e.getId()).toList();
        List<Long> personIds = persons.findBySupplierIdInOrderByIdDesc(supplierIds).stream()
                .map(p -> p.getId()).toList();
        // 3) 위험 서류 합본 (장비 + 인원)
        List<Map<String, Object>> all = new java.util.ArrayList<>();
        all.addAll(documentRisks(OwnerType.EQUIPMENT, equipIds));
        all.addAll(documentRisks(OwnerType.PERSON, personIds));
        return all.stream().limit(20).toList();
    }

    /**
     * BP 서명대기 위젯 — 자기 회사 DRAFT 작업계획서 중 5인 사인 미완(미서명/부분서명) 건수 + 목록.
     *
     * 근거: 사인은 DRAFT 단계에서 수집되고 제출은 5인 사인 완료가 게이트(WorkPlanService.allSigned).
     * 따라서 "서명 대기" = DRAFT 이면서 SIGNED < 5. batch group count 로 N+1 회피(PNG 미로드).
     */
    @GetMapping("/bp/pending-signatures")
    public Map<String, Object> bpPendingSignatures(@CurrentUser AuthenticatedUser actor) {
        if (actor.role() != Role.BP) throw ApiException.forbidden("BP_ONLY", "BP 전용");
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");

        List<WorkPlan> drafts = workPlans.findByBpCompanyIdAndStatusInOrderByIdDesc(
                actor.companyId(), List.of(WorkPlanStatus.DRAFT));
        Map<String, Object> body = new HashMap<>();
        if (drafts.isEmpty()) {
            body.put("count", 0L);
            body.put("items", List.of());
            return body;
        }
        List<Long> ids = drafts.stream().map(WorkPlan::getId).toList();
        Map<Long, Long> signedCount = new HashMap<>();
        for (Object[] row : signatures.countByStatusGroupedByWorkPlan(ids, SignatureStatus.SIGNED)) {
            signedCount.put((Long) row[0], (Long) row[1]);
        }
        List<WorkPlan> pending = drafts.stream()
                .filter(wp -> signedCount.getOrDefault(wp.getId(), 0L) < 5)
                .toList();

        List<Long> siteIds = pending.stream().map(WorkPlan::getSiteId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> siteNameMap = sites.findAllById(siteIds).stream()
                .collect(Collectors.toMap(Site::getId, Site::getName));

        body.put("count", (long) pending.size());
        body.put("items", pending.stream().limit(20).map(wp -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", wp.getId());
            r.put("title", wp.getTitle());
            r.put("site_id", wp.getSiteId());
            r.put("site_name", wp.getSiteId() != null ? siteNameMap.get(wp.getSiteId()) : null);
            r.put("work_date", wp.getWorkDate());
            r.put("status", wp.getStatus().name());
            r.put("signed_count", signedCount.getOrDefault(wp.getId(), 0L));
            r.put("required", 5);
            return r;
        }).toList());
        return body;
    }

    /** B3: BP 현장별 파이프라인 — 현장 단위 점검/검사/투입/서명대기/서류 요약(읽기전용). */
    @GetMapping("/bp/site-pipeline")
    public Map<String, Object> bpSitePipeline(@CurrentUser AuthenticatedUser actor) {
        return bpSitePipeline.sitePipeline(actor);
    }

    @GetMapping("/equipment-supplier/summary")
    public Map<String, Object> equipmentSupplierSummary(@CurrentUser AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER) throw ApiException.forbidden("EQ_SUPPLIER_ONLY", "장비공급사 전용");
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        return supplierSummary(actor, OwnerType.EQUIPMENT);
    }

    @GetMapping("/manpower-supplier/summary")
    public Map<String, Object> manpowerSupplierSummary(@CurrentUser AuthenticatedUser actor) {
        if (actor.role() != Role.MANPOWER_SUPPLIER) throw ApiException.forbidden("MP_SUPPLIER_ONLY", "인력공급사 전용");
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        return supplierSummary(actor, OwnerType.PERSON);
    }

    private Map<String, Object> supplierSummary(AuthenticatedUser actor, OwnerType ownerType) {
        Map<String, Object> body = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();

        List<SiteParticipant> myParticipations = participants
                .findByCompanyIdAndStatusOrderByIdDesc(actor.companyId(), SiteParticipantStatus.ACTIVE);
        List<Long> participatedSiteIds = myParticipations.stream().map(SiteParticipant::getSiteId).toList();

        // 자원 owner id 모음 (이 회사 소유) — 만료 카운트를 owner_type + owner_ids 로 좁히기 위함.
        // S-9: 장비공급사도 자기 조종원(인력) 운영하므로 EQUIPMENT_SUPPLIER 일 때도 인력 카운트 동시 노출.
        LocalDate maxDate = LocalDate.now().plusDays(30);
        List<Long> equipOwnerIds = List.of();
        List<Long> personOwnerIds = List.of();

        // 장비 통계 — EQUIPMENT_SUPPLIER 만 (MANPOWER_SUPPLIER 는 장비 없음).
        if (ownerType == OwnerType.EQUIPMENT) {
            counts.put("my_equipment", equipment.countBySupplierId(actor.companyId()));
            var myEq = equipment.findBySupplierIdOrderByIdDesc(actor.companyId());
            counts.put("equipment_assigned", myEq.stream().filter(e -> e.getCurrentSiteId() != null).count());
            equipOwnerIds = myEq.stream().map(e -> e.getId()).toList();
        }

        // 인력 통계 — 양쪽 supplier 다 운영 (장비공급사 = 조종원, 인력공급사 = 4역할+점검원/소장).
        counts.put("my_persons", persons.countBySupplierId(actor.companyId()));
        var myPersons = persons.findBySupplierIdInOrderByIdDesc(List.of(actor.companyId()));
        counts.put("persons_on_duty", myPersons.stream().filter(p -> p.getCurrentSiteId() != null).count());
        personOwnerIds = myPersons.stream().map(p -> p.getId()).toList();

        // 회사 자원 owner_type + owner_ids 만 카운트 — 다른 회사 서류 위험이 새지 않도록.
        long expEquip = equipOwnerIds.isEmpty() ? 0L
                : documents.countExpiringForOwners(OwnerType.EQUIPMENT, equipOwnerIds, maxDate);
        long expPerson = personOwnerIds.isEmpty() ? 0L
                : documents.countExpiringForOwners(OwnerType.PERSON, personOwnerIds, maxDate);
        counts.put("documents_expiring30d", expEquip + expPerson);
        if (ownerType == OwnerType.EQUIPMENT) {
            counts.put("documents_expiring_equipment", expEquip);
            counts.put("documents_expiring_persons", expPerson);
        }
        counts.put("participated_sites", (long) participatedSiteIds.size());
        body.put("counts", counts);

        // 통합 owner ids — document_risks 합본용.
        List<Long> myOwnerIds = ownerType == OwnerType.EQUIPMENT ? equipOwnerIds : personOwnerIds;

        body.put("sites", participatedSiteIds.isEmpty()
                ? List.of()
                : sites.findAllById(participatedSiteIds).stream().map(s -> {
                    Map<String, Object> r = new HashMap<>();
                    r.put("id", s.getId());
                    r.put("name", s.getName());
                    r.put("status", s.getStatus().name());
                    r.put("bp_company_id", s.getBpCompanyId());
                    return r;
                }).toList());

        body.put("recent_audit_logs", auditLog.recent(actor, 10).stream()
                .map(r -> AuditLogResponse.from(r.log())).toList());
        // S-6: 공급사 자기 회사 자원이 포함된 오늘 + 향후 7일 작업계획서.
        LocalDate today = LocalDate.now();
        var supplierUpcoming = workPlans.findUpcomingForSupplier(
                actor.companyId(), today, today.plusDays(7));
        counts.put("work_plans_upcoming", (long) supplierUpcoming.size());
        body.put("upcoming_work_plans", toWorkPlanItems(supplierUpcoming, 20));
        // 통계 카드는 master 만. 일반 직원은 빈 Map.
        if (!actor.isCompanyAdmin()) counts.clear();
        body.put("is_company_admin", actor.isCompanyAdmin());
        // V14: 만료 임박 + REJECTED + OCR_REVIEW_REQUIRED 위험 서류 chain head 만.
        // S-9: 장비공급사는 장비 + 조종원(인력) 위험을 합본해 보여줌.
        if (ownerType == OwnerType.EQUIPMENT) {
            List<Map<String, Object>> all = new java.util.ArrayList<>();
            all.addAll(documentRisks(OwnerType.EQUIPMENT, equipOwnerIds));
            all.addAll(documentRisks(OwnerType.PERSON, personOwnerIds));
            body.put("document_risks", all.stream().limit(20).toList());
        } else {
            body.put("document_risks", documentRisks(ownerType, myOwnerIds));
        }
        return body;
    }

    /**
     * 작업계획서를 대시보드 카드용 간단 형태로 변환. 자원 카운트는 batch query 로 한 번에 묶어서 N+1 회피.
     * 사이트명/BP명도 한 번에 캐시.
     */
    private List<Map<String, Object>> toWorkPlanItems(List<WorkPlan> plans, int limit) {
        if (plans.isEmpty()) return List.of();
        List<WorkPlan> capped = plans.stream().limit(limit).toList();
        List<Long> ids = capped.stream().map(WorkPlan::getId).toList();
        List<Long> siteIds = capped.stream().map(WorkPlan::getSiteId).distinct().toList();
        List<Long> bpIds = capped.stream().map(WorkPlan::getBpCompanyId).distinct().toList();

        Map<Long, Long> equipCount = wpe.findByWorkPlanIdIn(ids).stream()
                .collect(Collectors.groupingBy(WorkPlanEquipment::getWorkPlanId, Collectors.counting()));
        Map<Long, Long> personCount = wpp.findByWorkPlanIdIn(ids).stream()
                .collect(Collectors.groupingBy(WorkPlanPerson::getWorkPlanId, Collectors.counting()));
        Map<Long, String> siteNameMap = sites.findAllById(siteIds).stream()
                .collect(Collectors.toMap(Site::getId, Site::getName));
        Map<Long, String> bpNameMap = companies.findAllById(bpIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        return capped.stream().map(wp -> {
            Map<String, Object> r = new HashMap<>();
            r.put("id", wp.getId());
            r.put("title", wp.getTitle());
            r.put("site_id", wp.getSiteId());
            r.put("site_name", siteNameMap.get(wp.getSiteId()));
            r.put("bp_company_name", bpNameMap.get(wp.getBpCompanyId()));
            r.put("work_date", wp.getWorkDate());
            r.put("start_time", wp.getStartTime());
            r.put("end_time", wp.getEndTime());
            r.put("status", wp.getStatus().name());
            r.put("equipment_count", equipCount.getOrDefault(wp.getId(), 0L));
            r.put("person_count", personCount.getOrDefault(wp.getId(), 0L));
            return r;
        }).toList();
    }

    /** 자원 owner_id 들의 위험 서류를 dashboard 표시용 형태로 변환. owner_name 도 포함. */
    private List<Map<String, Object>> documentRisks(OwnerType ownerType, List<Long> ownerIds) {
        if (ownerIds.isEmpty()) return List.of();
        LocalDate maxDate = LocalDate.now().plusDays(30);
        List<Document> risky = documents.findRiskyForOwners(ownerType, ownerIds, maxDate);
        if (risky.isEmpty()) return List.of();
        Map<Long, DocumentType> typeCache = new HashMap<>();
        // owner_name batch lookup — 화면에 "누구 서류" 표시용. N+1 회피 위해 한 번에 fetch.
        Map<Long, String> ownerNameMap = new HashMap<>();
        List<Long> riskyOwnerIds = risky.stream().map(Document::getOwnerId).distinct().toList();
        if (ownerType == OwnerType.EQUIPMENT) {
            equipment.findAllById(riskyOwnerIds).forEach(e -> {
                String name = e.getVehicleNo() != null && !e.getVehicleNo().isBlank()
                        ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel() : "장비 #" + e.getId());
                ownerNameMap.put(e.getId(), name);
            });
        } else if (ownerType == OwnerType.PERSON) {
            persons.findAllById(riskyOwnerIds).forEach(p -> ownerNameMap.put(p.getId(), p.getName()));
        }
        return risky.stream().limit(20).map(d -> {
            DocumentType t = typeCache.computeIfAbsent(d.getDocumentTypeId(),
                    id -> documentTypes.findById(id).orElse(null));
            Map<String, Object> r = new HashMap<>();
            r.put("id", d.getId());
            r.put("owner_type", d.getOwnerType().name());
            r.put("owner_id", d.getOwnerId());
            r.put("owner_name", ownerNameMap.getOrDefault(d.getOwnerId(), "(삭제됨)"));
            r.put("document_type_id", d.getDocumentTypeId());
            r.put("document_type_name", t != null ? t.getName() : "(삭제됨)");
            r.put("expiry_date", d.getExpiryDate());
            r.put("verification_status", d.getVerificationStatus().name());
            // 위험 분류: EXPIRED | EXPIRING_SOON | REJECTED | OCR_REVIEW_REQUIRED
            String risk;
            if (d.getVerificationStatus() == com.skep.document.VerificationStatus.REJECTED) risk = "REJECTED";
            else if (d.getVerificationStatus() == com.skep.document.VerificationStatus.OCR_REVIEW_REQUIRED) risk = "OCR_REVIEW_REQUIRED";
            else if (d.getExpiryDate() != null && d.getExpiryDate().isBefore(LocalDate.now())) risk = "EXPIRED";
            else risk = "EXPIRING_SOON";
            r.put("risk", risk);
            return r;
        }).toList();
    }
}
