package com.skep.assignment;

import com.skep.assignment.dto.*;
import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentAssignmentStatus;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonAssignmentStatus;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 자원 ↔ 현장 배치/해제/이력/후보 조회 서비스.
 *
 * 핵심 정책:
 * - 자원은 한 번에 한 현장에만 배치된다 (DB UNIQUE INDEX 강제 + 코드에서도 enforce).
 * - 다른 현장에 배치돼 있으면 자동 해제 후 재배치.
 * - ADMIN/BP만 배치/해제 가능. 공급사는 자기 자원/자기 현장 조회만.
 * - 자원 supplier 가 site 의 ACTIVE 참여공급사여야 배치 가능.
 */
@Service
@Transactional
public class AssignmentService {

    private static final int EXPIRING_DAYS = 30;

    private final EquipmentAssignmentRepository eqAssignments;
    private final PersonAssignmentRepository personAssignments;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final CompanyRepository companies;
    private final DocumentRepository docRepo;
    private final DocumentTypeRepository docTypeRepo;
    private final AuditLogService auditLog;
    private final NotificationService notifications;

    public AssignmentService(EquipmentAssignmentRepository eqAssignments,
                             PersonAssignmentRepository personAssignments,
                             EquipmentRepository equipmentRepo,
                             PersonRepository personRepo,
                             SiteRepository sites,
                             SiteParticipantRepository participants,
                             CompanyRepository companies,
                             DocumentRepository docRepo,
                             DocumentTypeRepository docTypeRepo,
                             AuditLogService auditLog,
                             NotificationService notifications) {
        this.eqAssignments = eqAssignments;
        this.personAssignments = personAssignments;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.sites = sites;
        this.participants = participants;
        this.companies = companies;
        this.docRepo = docRepo;
        this.docTypeRepo = docTypeRepo;
        this.auditLog = auditLog;
        this.notifications = notifications;
    }

    // ==========================================================
    // Equipment 배치/해제/이력
    // ==========================================================

    public Equipment assignEquipment(Long equipmentId, AssignRequest req, AuthenticatedUser actor) {
        Equipment e = equipmentRepo.findById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
        if (e.getAssignmentStatus() == EquipmentAssignmentStatus.BROKEN) {
            throw ApiException.badRequest("EQUIPMENT_BROKEN", "고장 상태인 장비는 배치할 수 없습니다");
        }
        Site site = getSiteForAssignment(req.siteId());
        ensureCanManageSite(actor, site);
        ensureSupplierIsParticipant(site.getId(), e.getSupplierId(), "EQUIPMENT");
        // V14 정책: blocks_assignment=true 필수 서류 검증. override 는 ADMIN 만 + 사유 필수.
        List<String> docMissing = enforceAssignmentDocs(OwnerType.EQUIPMENT, e.getId(), req, actor);

        LocalDateTime now = LocalDateTime.now();

        // 기존 활성 배치가 있으면 해제
        eqAssignments.findByEquipmentIdAndReleasedAtIsNull(equipmentId).ifPresent(active -> {
            // 같은 현장으로의 재배치는 거부
            if (active.getSiteId().equals(req.siteId())) {
                throw ApiException.badRequest("ALREADY_ASSIGNED", "이미 같은 현장에 배치되어 있습니다");
            }
            // P2: 다른 현장에서 끌어올 때 기존 현장에 대한 권한도 확인 (또는 ADMIN)
            if (actor.role() != Role.ADMIN) {
                Site previousSite = sites.findById(active.getSiteId()).orElse(null);
                if (previousSite != null) {
                    try { ensureCanManageSite(actor, previousSite); }
                    catch (ApiException ex) {
                        throw ApiException.forbidden("CROSS_SITE_REASSIGN_DENIED",
                                "다른 BP 현장에 배치된 장비입니다. 기존 현장에서 먼저 해제 요청을 해주세요");
                    }
                }
            }
            Long previousSiteId = active.getSiteId();
            active.release(now, actor.id(), "현장 이동: site " + req.siteId());
            // 자동 해제도 기록한다 — "모든 배치/해제는 audit log 에 남는다" 정책.
            auditLog.record(actor, AuditAction.EQUIPMENT_UNASSIGNED, AuditTargetType.EQUIPMENT,
                    e.getId(), e.getSupplierId(), previousSiteId,
                    "{\"site_id\":" + previousSiteId + ",\"status\":\"ASSIGNED\"}",
                    "{\"reason\":\"auto_release_on_move\",\"new_site_id\":" + req.siteId() + "}");
        });

        // 새 활성 배치 생성
        eqAssignments.save(EquipmentAssignment.builder()
                .equipmentId(equipmentId)
                .siteId(req.siteId())
                .assignedAt(now)
                .assignedBy(actor.id())
                .note(req.note())
                .build());

        e.assignToSite(req.siteId(), now);
        String afterJson = "{\"site_id\":" + site.getId() + ",\"status\":\"ASSIGNED\""
                + (docMissing.isEmpty() ? "" : ",\"override\":true,\"override_reason\":\""
                        + escape(req.overrideReason()) + "\",\"missing\":\""
                        + escape(String.join(", ", docMissing)) + "\"")
                + "}";
        auditLog.record(actor, AuditAction.EQUIPMENT_ASSIGNED, AuditTargetType.EQUIPMENT,
                e.getId(), e.getSupplierId(), site.getId(),
                null,
                afterJson);
        if (!docMissing.isEmpty()) {
            notifications.sendToCompany(e.getSupplierId(), NotificationType.ASSIGNMENT_OVERRIDDEN,
                    "장비 강제 배치",
                    "서류 미비 상태로 강제 배치됨. 사유: " + req.overrideReason()
                            + " / 누락: " + String.join(", ", docMissing),
                    "EQUIPMENT", e.getId(), site.getId());
        }
        return e;
    }

    public Equipment releaseEquipment(Long equipmentId, ReleaseRequest req, AuthenticatedUser actor) {
        Equipment e = equipmentRepo.findById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
        EquipmentAssignment active = eqAssignments.findByEquipmentIdAndReleasedAtIsNull(equipmentId)
                .orElseThrow(() -> ApiException.badRequest("NOT_ASSIGNED", "현재 배치된 현장이 없습니다"));
        Site site = sites.findById(active.getSiteId())
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanManageSite(actor, site);

        active.release(LocalDateTime.now(), actor.id(), req != null ? req.releaseReason() : null);
        e.releaseFromSite();
        auditLog.record(actor, AuditAction.EQUIPMENT_UNASSIGNED, AuditTargetType.EQUIPMENT,
                e.getId(), e.getSupplierId(), site.getId(),
                "{\"site_id\":" + site.getId() + ",\"status\":\"ASSIGNED\"}",
                "{\"status\":\"" + e.getAssignmentStatus().name() + "\"}");
        return e;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> equipmentHistory(Long equipmentId, AuthenticatedUser actor) {
        Equipment e = equipmentRepo.findById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
        ensureCanAccessEquipment(actor, e);
        List<EquipmentAssignment> rows = eqAssignments.findByEquipmentIdOrderByAssignedAtDesc(equipmentId);
        Map<Long, Site> siteMap = siteMap(rows.stream().map(EquipmentAssignment::getSiteId).toList());
        return rows.stream()
                .map(a -> AssignmentResponse.fromEquipment(a, siteName(siteMap, a.getSiteId())))
                .toList();
    }

    // ==========================================================
    // Person 배치/해제/이력
    // ==========================================================

    public Person assignPerson(Long personId, AssignRequest req, AuthenticatedUser actor) {
        Person p = personRepo.findById(personId)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원을 찾을 수 없습니다"));
        if (p.getAssignmentStatus() == PersonAssignmentStatus.INACTIVE) {
            throw ApiException.badRequest("PERSON_INACTIVE", "비활성 상태인 인원은 배치할 수 없습니다");
        }
        Site site = getSiteForAssignment(req.siteId());
        ensureCanManageSite(actor, site);
        ensureSupplierIsParticipant(site.getId(), p.getSupplierId(), "PERSON");
        List<String> docMissing = enforceAssignmentDocs(OwnerType.PERSON, p.getId(), req, actor);

        LocalDateTime now = LocalDateTime.now();

        personAssignments.findByPersonIdAndReleasedAtIsNull(personId).ifPresent(active -> {
            if (active.getSiteId().equals(req.siteId())) {
                throw ApiException.badRequest("ALREADY_ASSIGNED", "이미 같은 현장에 배치되어 있습니다");
            }
            // P2: 기존 현장 권한 확인 (또는 ADMIN)
            if (actor.role() != Role.ADMIN) {
                Site previousSite = sites.findById(active.getSiteId()).orElse(null);
                if (previousSite != null) {
                    try { ensureCanManageSite(actor, previousSite); }
                    catch (ApiException ex) {
                        throw ApiException.forbidden("CROSS_SITE_REASSIGN_DENIED",
                                "다른 BP 현장에 배치된 인원입니다. 기존 현장에서 먼저 해제 요청을 해주세요");
                    }
                }
            }
            Long previousSiteId = active.getSiteId();
            active.release(now, actor.id(), "현장 이동: site " + req.siteId());
            auditLog.record(actor, AuditAction.PERSON_UNASSIGNED, AuditTargetType.PERSON,
                    p.getId(), p.getSupplierId(), previousSiteId,
                    "{\"site_id\":" + previousSiteId + ",\"status\":\"ON_DUTY\"}",
                    "{\"reason\":\"auto_release_on_move\",\"new_site_id\":" + req.siteId() + "}");
        });

        personAssignments.save(PersonAssignment.builder()
                .personId(personId)
                .siteId(req.siteId())
                .assignedAt(now)
                .assignedBy(actor.id())
                .note(req.note())
                .build());

        p.assignToSite(req.siteId(), now);
        String afterJson = "{\"site_id\":" + site.getId() + ",\"status\":\"ON_DUTY\""
                + (docMissing.isEmpty() ? "" : ",\"override\":true,\"override_reason\":\""
                        + escape(req.overrideReason()) + "\",\"missing\":\""
                        + escape(String.join(", ", docMissing)) + "\"")
                + "}";
        auditLog.record(actor, AuditAction.PERSON_ASSIGNED, AuditTargetType.PERSON,
                p.getId(), p.getSupplierId(), site.getId(),
                null,
                afterJson);
        if (!docMissing.isEmpty()) {
            notifications.sendToCompany(p.getSupplierId(), NotificationType.ASSIGNMENT_OVERRIDDEN,
                    "인원 강제 배치",
                    "서류 미비 상태로 강제 배치됨. 사유: " + req.overrideReason()
                            + " / 누락: " + String.join(", ", docMissing),
                    "PERSON", p.getId(), site.getId());
        }
        return p;
    }

    public Person releasePerson(Long personId, ReleaseRequest req, AuthenticatedUser actor) {
        Person p = personRepo.findById(personId)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원을 찾을 수 없습니다"));
        PersonAssignment active = personAssignments.findByPersonIdAndReleasedAtIsNull(personId)
                .orElseThrow(() -> ApiException.badRequest("NOT_ASSIGNED", "현재 배치된 현장이 없습니다"));
        Site site = sites.findById(active.getSiteId())
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanManageSite(actor, site);

        active.release(LocalDateTime.now(), actor.id(), req != null ? req.releaseReason() : null);
        p.releaseFromSite();
        auditLog.record(actor, AuditAction.PERSON_UNASSIGNED, AuditTargetType.PERSON,
                p.getId(), p.getSupplierId(), site.getId(),
                "{\"site_id\":" + site.getId() + ",\"status\":\"ON_DUTY\"}",
                "{\"status\":\"" + p.getAssignmentStatus().name() + "\"}");
        return p;
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> personHistory(Long personId, AuthenticatedUser actor) {
        Person p = personRepo.findById(personId)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원을 찾을 수 없습니다"));
        ensureCanAccessPerson(actor, p);
        List<PersonAssignment> rows = personAssignments.findByPersonIdOrderByAssignedAtDesc(personId);
        Map<Long, Site> siteMap = siteMap(rows.stream().map(PersonAssignment::getSiteId).toList());
        return rows.stream()
                .map(a -> AssignmentResponse.fromPerson(a, siteName(siteMap, a.getSiteId())))
                .toList();
    }

    // ==========================================================
    // 현장의 배치된 자원 조회
    // ==========================================================

    @Transactional(readOnly = true)
    public List<Equipment> equipmentOnSite(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanViewSite(actor, site);
        List<EquipmentAssignment> active = eqAssignments.findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(siteId);
        if (active.isEmpty()) return List.of();
        List<Long> ids = active.stream().map(EquipmentAssignment::getEquipmentId).toList();
        return equipmentRepo.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public List<Person> personsOnSite(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanViewSite(actor, site);
        List<PersonAssignment> active = personAssignments.findBySiteIdAndReleasedAtIsNullOrderByAssignedAtDesc(siteId);
        if (active.isEmpty()) return List.of();
        List<Long> ids = active.stream().map(PersonAssignment::getPersonId).toList();
        return personRepo.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> siteEquipmentHistory(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanViewSite(actor, site);
        return eqAssignments.findBySiteIdOrderByAssignedAtDesc(siteId).stream()
                .map(a -> AssignmentResponse.fromEquipment(a, site.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> sitePersonHistory(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanViewSite(actor, site);
        return personAssignments.findBySiteIdOrderByAssignedAtDesc(siteId).stream()
                .map(a -> AssignmentResponse.fromPerson(a, site.getName()))
                .toList();
    }

    // ==========================================================
    // 현장 후보 조회 (참여 공급사의 자원)
    // ==========================================================

    @Transactional(readOnly = true)
    public List<EquipmentCandidateResponse> equipmentCandidates(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanManageSite(actor, site);

        // 현장 ACTIVE 참여 공급사 (EQUIPMENT 타입만)
        List<Long> supplierIds = participants.findBySiteIdOrderByIdDesc(siteId).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                .filter(p -> com.skep.site.SiteParticipantType.EQUIPMENT_SUPPLIER == p.getParticipantType())
                .map(SiteParticipant::getCompanyId)
                .distinct()
                .toList();
        if (supplierIds.isEmpty()) return List.of();

        List<Equipment> equipmentList = equipmentRepo.findBySupplierIdInOrderByIdDesc(supplierIds);
        if (equipmentList.isEmpty()) return List.of();

        Map<Long, Company> companyMap = companyMap(supplierIds);
        Map<Long, Site> siteMap = siteMap(equipmentList.stream().map(Equipment::getCurrentSiteId).toList());
        List<Long> equipmentIds = equipmentList.stream().map(Equipment::getId).toList();
        Set<Long> previouslyOnSite = new HashSet<>(eqAssignments.findEquipmentIdsPreviouslyOnSite(siteId, equipmentIds));
        Map<Long, Long> expiring = expiringCounts(OwnerType.EQUIPMENT, equipmentIds);
        // V14 정책: required+active document_types 기준으로 누락 서류 수 계산.
        Map<Long, Long> missing = missingRequiredCounts(OwnerType.EQUIPMENT, equipmentIds);

        return equipmentList.stream().map(e -> {
            Company supplier = companyMap.get(e.getSupplierId());
            String supplierName = supplier != null ? supplier.getName() : null;
            Site current = e.getCurrentSiteId() != null ? siteMap.get(e.getCurrentSiteId()) : null;
            String currentName = current != null ? current.getName() : null;
            return EquipmentCandidateResponse.from(
                    e,
                    supplierName,
                    currentName,
                    previouslyOnSite.contains(e.getId()),
                    expiring.getOrDefault(e.getId(), 0L),
                    missing.getOrDefault(e.getId(), 0L)
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonCandidateResponse> personCandidates(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanManageSite(actor, site);

        // 현장 ACTIVE 참여 공급사 (EQUIPMENT + MANPOWER 둘 다 — 조종원도 인원이라)
        List<Long> supplierIds = participants.findBySiteIdOrderByIdDesc(siteId).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                .map(SiteParticipant::getCompanyId)
                .distinct()
                .toList();
        if (supplierIds.isEmpty()) return List.of();

        List<Person> personList = personRepo.findBySupplierIdInOrderByIdDesc(supplierIds);
        if (personList.isEmpty()) return List.of();

        Map<Long, Company> companyMap = companyMap(supplierIds);
        Map<Long, Site> siteMap = siteMap(personList.stream().map(Person::getCurrentSiteId).toList());
        List<Long> personIds = personList.stream().map(Person::getId).toList();
        Set<Long> previouslyOnSite = new HashSet<>(personAssignments.findPersonIdsPreviouslyOnSite(siteId, personIds));
        Map<Long, Long> expiring = expiringCounts(OwnerType.PERSON, personIds);
        Map<Long, Long> missing = missingRequiredCounts(OwnerType.PERSON, personIds);

        return personList.stream().map(p -> {
            Company supplier = companyMap.get(p.getSupplierId());
            String supplierName = supplier != null ? supplier.getName() : null;
            Site current = p.getCurrentSiteId() != null ? siteMap.get(p.getCurrentSiteId()) : null;
            String currentName = current != null ? current.getName() : null;
            return PersonCandidateResponse.from(
                    p,
                    supplierName,
                    currentName,
                    previouslyOnSite.contains(p.getId()),
                    expiring.getOrDefault(p.getId(), 0L),
                    missing.getOrDefault(p.getId(), 0L)
            );
        }).toList();
    }

    // ==========================================================
    // 헬퍼
    // ==========================================================

    /** 자원과 함께 현재 현장 이름까지 가져오기 위해 service 호출자가 사용. */
    @Transactional(readOnly = true)
    public String resolveSiteName(Long siteId) {
        if (siteId == null) return null;
        return sites.findById(siteId).map(Site::getName).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> siteNamesByIds(Collection<Long> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) return Map.of();
        return siteMap(siteIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));
    }

    private Site getSiteForAssignment(Long siteId) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        if (site.getStatus() != com.skep.site.SiteStatus.ACTIVE) {
            throw ApiException.badRequest("SITE_NOT_ACTIVE", "활성 상태가 아닌 현장에는 배치할 수 없습니다");
        }
        return site;
    }

    private void ensureSupplierIsParticipant(Long siteId, Long supplierId, String resourceLabel) {
        boolean ok = participants.existsBySiteIdAndCompanyIdAndStatus(siteId, supplierId, SiteParticipantStatus.ACTIVE);
        if (!ok) {
            throw ApiException.badRequest("SUPPLIER_NOT_PARTICIPANT",
                    resourceLabel + " 자원의 공급사가 해당 현장의 참여업체가 아닙니다");
        }
    }

    private void ensureCanManageSite(AuthenticatedUser actor, Site site) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
            }
            if (site.getBpCompanyId().equals(actor.companyId())) return;
        }
        throw ApiException.forbidden("ASSIGNMENT_DENIED", "배치/해제 권한이 없습니다");
    }

    private void ensureCanViewSite(AuthenticatedUser actor, Site site) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP && actor.companyId() != null
                && site.getBpCompanyId().equals(actor.companyId())) return;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && actor.companyId() != null
                && participants.existsBySiteIdAndCompanyIdAndStatus(site.getId(), actor.companyId(), SiteParticipantStatus.ACTIVE)) {
            return;
        }
        throw ApiException.forbidden("SITE_ACCESS_DENIED", "현장 접근 권한이 없습니다");
    }

    private void ensureCanAccessEquipment(AuthenticatedUser actor, Equipment e) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            if (!e.getSupplierId().equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사 장비만 조회할 수 있습니다");
            }
            return;
        }
        if (actor.role() == Role.BP) {
            // BP: 자기 회사 현장에 ACTIVE 참여 중인 공급사 장비 + 자기 회사 직속 장비.
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "회사 미설정");
            }
            if (e.getSupplierId().equals(actor.companyId())) return;
            boolean visible = participants.findByCompanyIdAndStatusOrderByIdDesc(e.getSupplierId(), SiteParticipantStatus.ACTIVE).stream()
                    .anyMatch(p -> sites.findById(p.getSiteId())
                            .map(s -> actor.companyId().equals(s.getBpCompanyId()))
                            .orElse(false));
            if (!visible) {
                throw ApiException.forbidden("EQUIPMENT_ASSIGN_DENIED",
                        "자기 현장 참여 공급사 장비만 조회할 수 있습니다");
            }
            return;
        }
        // MANPOWER_SUPPLIER / WORKER 등 차단.
        throw ApiException.forbidden("EQUIPMENT_ASSIGN_DENIED", "장비 배치 이력 조회 권한이 없습니다");
    }

    private void ensureCanAccessPerson(AuthenticatedUser actor, Person p) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (!p.getSupplierId().equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_COMPANY", "본인 회사 인원만 조회할 수 있습니다");
            }
            return;
        }
        if (actor.role() == Role.BP) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "회사 미설정");
            }
            // #5: BP 자기 회사 직속 인원 OK
            if (p.getSupplierId().equals(actor.companyId())) return;
            boolean visible = participants.findByCompanyIdAndStatusOrderByIdDesc(p.getSupplierId(), SiteParticipantStatus.ACTIVE).stream()
                    .anyMatch(part -> sites.findById(part.getSiteId())
                            .map(s -> actor.companyId().equals(s.getBpCompanyId()))
                            .orElse(false));
            if (!visible) {
                throw ApiException.forbidden("PERSON_ASSIGN_DENIED",
                        "자기 현장 참여 공급사 인원만 조회할 수 있습니다");
            }
            return;
        }
        throw ApiException.forbidden("PERSON_ASSIGN_DENIED", "인원 배치 이력 조회 권한이 없습니다");
    }

    private Map<Long, Site> siteMap(Collection<Long> siteIds) {
        List<Long> ids = siteIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return sites.findAllById(ids).stream().collect(Collectors.toMap(Site::getId, Function.identity()));
    }

    private Map<Long, Company> companyMap(Collection<Long> companyIds) {
        List<Long> ids = companyIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return companies.findAllById(ids).stream().collect(Collectors.toMap(Company::getId, Function.identity()));
    }

    private static String siteName(Map<Long, Site> map, Long id) {
        Site s = map.get(id);
        return s != null ? s.getName() : null;
    }

    private Map<Long, Long> expiringCounts(OwnerType ownerType, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        LocalDate maxDate = LocalDate.now().plusDays(EXPIRING_DAYS);
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : docRepo.countExpiringGroupedByOwner(ownerType, ids, maxDate)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    /**
     * 자원 owner_id 별로 누락 서류 수를 계산.
     *
     * - typeFilter == REQUIRED  : required=true 인 type 기준 (표시용 카운트, missing_documents 컬럼)
     * - typeFilter == BLOCKING  : blocks_assignment=true 인 type 기준 (실제 배차 차단 정책)
     *
     * 누락 = 해당 type 중에서 자원이 보유한 "유효 서류"(verified=true + 만료 안 됨) 의 type 에 없는 type 수.
     * REJECTED 는 verified=false 이므로 자동으로 누락 처리된다.
     */
    private Map<Long, Long> missingDocCounts(OwnerType ownerType, List<Long> ownerIds, MissingFilter typeFilter) {
        if (ownerIds.isEmpty()) return Map.of();
        List<DocumentType> targetTypes = switch (typeFilter) {
            case REQUIRED -> docTypeRepo.findByAppliesToAndRequiredTrueAndActiveTrueOrderByIdAsc(ownerType);
            case BLOCKING -> docTypeRepo.findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc(ownerType);
        };
        if (targetTypes.isEmpty()) {
            Map<Long, Long> zero = new HashMap<>();
            for (Long id : ownerIds) zero.put(id, 0L);
            return zero;
        }
        Set<Long> targetTypeIds = targetTypes.stream().map(DocumentType::getId).collect(Collectors.toSet());
        int targetCount = targetTypeIds.size();

        LocalDate today = LocalDate.now();
        Map<Long, Set<Long>> validTypesByOwner = new HashMap<>();
        for (Object[] row : docRepo.findValidVerifiedTypesByOwners(ownerType, ownerIds, today)) {
            Long ownerId = (Long) row[0];
            Long typeId = (Long) row[1];
            if (targetTypeIds.contains(typeId)) {
                validTypesByOwner.computeIfAbsent(ownerId, k -> new HashSet<>()).add(typeId);
            }
        }

        Map<Long, Long> result = new HashMap<>();
        for (Long ownerId : ownerIds) {
            int have = validTypesByOwner.getOrDefault(ownerId, Set.of()).size();
            result.put(ownerId, (long) Math.max(0, targetCount - have));
        }
        return result;
    }

    /** 후보 표시용: required=true 전체 누락 수. */
    private Map<Long, Long> missingRequiredCounts(OwnerType ownerType, List<Long> ownerIds) {
        return missingDocCounts(ownerType, ownerIds, MissingFilter.REQUIRED);
    }

    /**
     * 배치 직전 서류 검증.
     *
     * - blocks_assignment=true 필수 서류 중 미보유/만료/REJECTED 가 있으면:
     *   - override=true && actor=ADMIN && override_reason 채워짐 → 통과 (audit 에 missing 기록)
     *   - 그 외 → DOCUMENTS_BLOCKED 거부
     *
     * 반환: 누락된 서류 type 이름 목록. 비어있으면 정상, 있으면 override 통과한 케이스.
     */
    private List<String> enforceAssignmentDocs(OwnerType ownerType, Long ownerId,
                                               AssignRequest req, AuthenticatedUser actor) {
        List<DocumentType> blocking =
                docTypeRepo.findByAppliesToAndBlocksAssignmentTrueAndActiveTrueOrderByIdAsc(ownerType);
        if (blocking.isEmpty()) return List.of();

        Set<Long> blockingIds = blocking.stream().map(DocumentType::getId).collect(Collectors.toSet());
        Set<Long> validIds = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (Object[] row : docRepo.findValidVerifiedTypesByOwners(ownerType, List.of(ownerId), today)) {
            Long typeId = (Long) row[1];
            if (blockingIds.contains(typeId)) validIds.add(typeId);
        }
        List<String> missing = blocking.stream()
                .filter(t -> !validIds.contains(t.getId()))
                .map(DocumentType::getName)
                .toList();
        if (missing.isEmpty()) return List.of();

        // 누락 있음. override 검사.
        boolean override = req != null && Boolean.TRUE.equals(req.override());
        if (!override) {
            throw ApiException.badRequest("DOCUMENTS_BLOCKED",
                    "필수 서류가 누락되거나 만료/반려되어 배치할 수 없습니다: " + String.join(", ", missing));
        }
        if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("OVERRIDE_ADMIN_ONLY",
                    "서류 미비 강제 진행은 관리자만 가능합니다");
        }
        if (req.overrideReason() == null || req.overrideReason().isBlank()) {
            throw ApiException.badRequest("OVERRIDE_REASON_REQUIRED",
                    "강제 진행 시 사유는 필수입니다");
        }
        return missing;
    }

    private enum MissingFilter { REQUIRED, BLOCKING }

    /** JSON 문자열 값에 들어갈 ", \, 줄바꿈을 escape. audit after_json 작성용. */
    private static String escape(String s) {
        return com.skep.common.SafeText.escapeJson(s);
    }
}
