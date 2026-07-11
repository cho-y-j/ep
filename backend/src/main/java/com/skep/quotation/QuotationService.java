package com.skep.quotation;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.notification.NotificationLabels;
import com.skep.clientorg.ClientOrg;
import com.skep.clientorg.ClientOrgRepository;
import com.skep.quotation.proposal.QuotationProposalRepository;
import com.skep.quotation.proposal.QuotationProposalStatus;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentCategory;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.person.PersonRole;
import com.skep.notification.NotificationType;
import com.skep.quotation.dto.*;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.alimtalk.AlimTalkService;
import com.skep.alimtalk.AlimTalkTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * S-10: 장비 견적 요청 도메인 서비스.
 *
 * 흐름:
 *   1. BP/ADMIN 가 candidates() 로 사이트 ACTIVE 참여 EQUIPMENT_SUPPLIER 들의 가용 장비 조회
 *   2. create() 로 (supplier × equipment) targets 다중 생성 + 공급사 알림
 *   3. 공급사가 각 target 별 respond() (수락/거부)
 *   4. BP/ADMIN 가 ACCEPTED target 을 finalize() — WorkPlan 자동 생성/매칭 + work_plan_equipment add (가격 저장)
 *
 * 권한:
 *   - ADMIN: 모두
 *   - BP: 자기 사이트 견적 + 자기 BP 회사 ID 가 sites.bp_company_id 와 일치
 *   - EQUIPMENT_SUPPLIER: 자기에게 온 target read + respond
 *   - 그 외: 차단
 */
@Service
@Transactional
public class QuotationService {

    private final QuotationRequestRepository requests;
    private final QuotationRequestTargetRepository targets;
    private final SiteRepository sites;
    private final CompanyRepository companies;
    private final ClientOrgRepository clientOrgs;
    private final QuotationProposalRepository proposals;
    private final EquipmentRepository equipmentRepo;
    private final UserRepository users;
    private final PersonRepository personRepo;
    private final NotificationService notifications;
    private final AuditLogService auditLog;
    private final AlimTalkService alimTalk;
    /** 후보 "이전 투입" 배지 — 이 BP 견적에 배차된 이력 있는 자원 조회. */
    private final com.skep.quotation.dispatch.DispatchedEquipmentRepository dispatchedEquipments;
    private final com.skep.quotation.dispatch.DispatchedPersonRepository dispatchedPersons;
    /** S-12+: 후보 BLOCKED 제외 필터 — 공급사/장비 컴플라이언스 확인. @Lazy 순환 의존 회피. */
    private final com.skep.compliance.ComplianceService compliance;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSenderProvider;
    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}")
    private String mailFrom;

    public QuotationService(QuotationRequestRepository requests, QuotationRequestTargetRepository targets,
                            SiteRepository sites,
                            CompanyRepository companies, ClientOrgRepository clientOrgs,
                            QuotationProposalRepository proposals,
                            EquipmentRepository equipmentRepo,
                            UserRepository users,
                            PersonRepository personRepo,
                            NotificationService notifications, AuditLogService auditLog,
                            @org.springframework.context.annotation.Lazy
                            com.skep.compliance.ComplianceService compliance,
                            org.springframework.beans.factory.ObjectProvider<org.springframework.mail.javamail.JavaMailSender> mailSenderProvider,
                            AlimTalkService alimTalk,
                            com.skep.quotation.dispatch.DispatchedEquipmentRepository dispatchedEquipments,
                            com.skep.quotation.dispatch.DispatchedPersonRepository dispatchedPersons) {
        this.requests = requests;
        this.targets = targets;
        this.sites = sites;
        this.companies = companies;
        this.clientOrgs = clientOrgs;
        this.proposals = proposals;
        this.equipmentRepo = equipmentRepo;
        this.users = users;
        this.personRepo = personRepo;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.compliance = compliance;
        this.mailSenderProvider = mailSenderProvider;
        this.alimTalk = alimTalk;
        this.dispatchedEquipments = dispatchedEquipments;
        this.dispatchedPersons = dispatchedPersons;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 후보 조회
    // ──────────────────────────────────────────────────────────────────────

    /** Site-C: site 무관 — 카테고리 매칭되는 전체 EQUIPMENT 공급사 풀. ClientOrg 이력은 응답에 포함. */
    @Transactional(readOnly = true)
    public List<QuotationCandidateResponse> candidates(EquipmentCategory category, AuthenticatedUser actor) {
        ensureBpOrAdmin(actor);

        List<Company> suppliers = companies.findByType(com.skep.company.CompanyType.EQUIPMENT);
        Map<Long, String> supplierName = new HashMap<>();
        List<Long> supplierIds = new ArrayList<>();
        for (Company c : suppliers) {
            try {
                var rc = compliance.forCompany(c.getId(), actor);
                if (!rc.readyForWorkPlan()) continue;
            } catch (Exception ignored) { continue; }
            supplierName.put(c.getId(), c.getName());
            supplierIds.add(c.getId());
        }
        if (supplierIds.isEmpty()) return List.of();

        List<Equipment> all = equipmentRepo.findBySupplierIdInOrderByIdDesc(supplierIds);

        // pass 1: compliance 통과 장비 수집 + rc 만료 개수 보관 (배지용 — 필터·개수 불변)
        List<Equipment> eligible = new ArrayList<>();
        Map<Long, Integer> expiringByEq = new HashMap<>();
        for (Equipment e : all) {
            if (category != null && e.getCategory() != category) continue;
            try {
                var rc = compliance.forEquipment(e.getId(), actor);
                if (!rc.readyForWorkPlan()) continue;
                expiringByEq.put(e.getId(), rc.expiringCount());
            } catch (Exception ignored) { continue; }
            eligible.add(e);
        }

        // 이전투입: 이 BP 견적에 배차된 이력 있는 장비 id 1회 조회. BP=actor.companyId(), 없으면(ADMIN) 미표시(null).
        Long bpCompanyId = actor.companyId();
        Set<Long> dispatchedIds = (bpCompanyId != null && !eligible.isEmpty())
                ? dispatchedEquipments.findDispatchedEquipmentIdsForBp(
                        bpCompanyId, eligible.stream().map(Equipment::getId).toList())
                : Set.of();

        // pass 2: item 생성
        Map<Long, List<QuotationCandidateResponse.EquipmentItem>> grouped = new LinkedHashMap<>();
        for (Long sid : supplierIds) grouped.put(sid, new ArrayList<>());
        Map<Long, String> siteName = new HashMap<>();
        for (Equipment e : eligible) {
            Long curSite = e.getCurrentSiteId();
            String curSiteName = curSite != null
                    ? siteName.computeIfAbsent(curSite, id -> sites.findById(id).map(Site::getName).orElse("?"))
                    : null;
            Boolean previouslyDispatched = bpCompanyId != null ? dispatchedIds.contains(e.getId()) : null;
            grouped.get(e.getSupplierId()).add(new QuotationCandidateResponse.EquipmentItem(
                    e.getId(), e.getVehicleNo(), e.getModel(), e.getManufacturer(),
                    e.getYear(), e.getCategory(), e.getSerialNumber(),
                    e.getPhotoKey() != null, curSite, curSiteName,
                    previouslyDispatched, Boolean.TRUE, expiringByEq.getOrDefault(e.getId(), 0)));
        }

        return supplierIds.stream()
                .filter(sid -> !grouped.get(sid).isEmpty())
                .map(sid -> new QuotationCandidateResponse(
                        sid, supplierName.getOrDefault(sid, "회사 #" + sid), grouped.get(sid)))
                .toList();
    }

    /** Site-C: site 무관 — 역할 가능한 전체 MANPOWER 공급사 풀. */
    @Transactional(readOnly = true)
    public List<com.skep.quotation.dto.QuotationManpowerCandidateResponse> manpowerCandidates(
            PersonRole role, AuthenticatedUser actor) {
        ensureBpOrAdmin(actor);

        List<Company> suppliers = companies.findByType(com.skep.company.CompanyType.MANPOWER);
        Map<Long, String> supplierName = new HashMap<>();
        List<Long> supplierIds = new ArrayList<>();
        for (Company c : suppliers) {
            try {
                var rc = compliance.forCompany(c.getId(), actor);
                if (!rc.readyForWorkPlan()) continue;
            } catch (Exception ignored) { continue; }
            supplierName.put(c.getId(), c.getName());
            supplierIds.add(c.getId());
        }
        if (supplierIds.isEmpty()) return List.of();

        List<Person> allPersons = personRepo.findBySupplierIdInOrderByIdDesc(supplierIds);

        // pass 1: compliance 통과 인원 수집 + rc 만료 개수 보관 (배지용 — 필터·개수 불변)
        List<Person> eligible = new ArrayList<>();
        Map<Long, Integer> expiringByPerson = new HashMap<>();
        for (Person p : allPersons) {
            if (role != null && (p.getRoles() == null || !p.getRoles().contains(role))) continue;
            try {
                var rc = compliance.forPerson(p.getId(), actor);
                if (!rc.readyForWorkPlan()) continue;
                expiringByPerson.put(p.getId(), rc.expiringCount());
            } catch (Exception ignored) { continue; }
            eligible.add(p);
        }

        // 이전투입: 이 BP 견적에 배차된 이력 있는 인원 id 1회 조회. BP=actor.companyId(), 없으면(ADMIN) 미표시(null).
        Long bpCompanyId = actor.companyId();
        Set<Long> dispatchedIds = (bpCompanyId != null && !eligible.isEmpty())
                ? dispatchedPersons.findDispatchedPersonIdsForBp(
                        bpCompanyId, eligible.stream().map(Person::getId).toList())
                : Set.of();

        // pass 2: item 생성
        Map<Long, List<com.skep.quotation.dto.QuotationManpowerCandidateResponse.PersonItem>> grouped = new LinkedHashMap<>();
        for (Long sid : supplierIds) grouped.put(sid, new ArrayList<>());
        for (Person p : eligible) {
            Boolean previouslyDispatched = bpCompanyId != null ? dispatchedIds.contains(p.getId()) : null;
            grouped.get(p.getSupplierId()).add(
                    new com.skep.quotation.dto.QuotationManpowerCandidateResponse.PersonItem(
                            p.getId(), p.getName(), p.getJobTitle(), p.getPhone(),
                            p.getEmployeeNo(), p.getRoles(), p.getPhotoKey() != null,
                            previouslyDispatched, Boolean.TRUE, expiringByPerson.getOrDefault(p.getId(), 0)));
        }

        return supplierIds.stream()
                .filter(sid -> !grouped.get(sid).isEmpty())
                .map(sid -> new com.skep.quotation.dto.QuotationManpowerCandidateResponse(
                        sid, supplierName.getOrDefault(sid, "회사 #" + sid), grouped.get(sid)))
                .toList();
    }

    private void ensureBpOrAdmin(AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) { requireCompany(actor); return; }
        throw ApiException.forbidden("BP_ADMIN_ONLY", "BP 또는 ADMIN 만 가능합니다");
    }

    // ──────────────────────────────────────────────────────────────────────
    // 생성
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 한 현장에 장비 + 인력 N역할을 한 묶음으로 발송. 한 트랜잭션, 같은 bundle_id 공유.
     * 인원 중복 (서로 다른 역할 행에 같은 person_id) 자동 차단.
     */
    public java.util.List<QuotationRequest> createBundle(CreateQuotationBundleRequest req, AuthenticatedUser actor) {
        if (req.equipment() == null && (req.manpower() == null || req.manpower().isEmpty())) {
            throw ApiException.badRequest("BUNDLE_EMPTY", "장비 또는 인력 중 최소 1건은 필요합니다");
        }
        // 인원 중복 검사
        if (req.manpower() != null) {
            java.util.Set<PersonRole> seenRoles = new java.util.HashSet<>();
            java.util.Set<Long> seenPersons = new java.util.HashSet<>();
            for (var it : req.manpower()) {
                if (!seenRoles.add(it.role())) {
                    throw ApiException.badRequest("DUPLICATE_ROLE",
                            "같은 역할이 중복되었습니다: " + it.role());
                }
                for (var t : it.targets()) {
                    if (!seenPersons.add(t.personId())) {
                        throw ApiException.badRequest("DUPLICATE_PERSON",
                                "인원 #" + t.personId() + " 가 여러 역할 행에 중복 선택되었습니다");
                    }
                }
            }
        }

        java.util.UUID bundleId = java.util.UUID.randomUUID();
        java.util.List<QuotationRequest> result = new java.util.ArrayList<>();

        if (req.equipment() != null) {
            var eq = req.equipment();
            var inner = new CreateQuotationRequest(
                    req.siteId(), req.workPeriodStart(), req.workPeriodEnd(),
                    QuotationRequestType.EQUIPMENT,
                    eq.category(), null,
                    eq.specText(),
                    eq.proposedDailyRate(), eq.proposedMonthlyRate(),
                    eq.count(), req.notes(),
                    req.onBehalfOfBpCompanyId(),
                    bundleId,
                    eq.targets().stream()
                            .map(t -> new CreateQuotationRequest.TargetInput(
                                    t.supplierCompanyId(), t.equipmentId(), null))
                            .toList()
            );
            result.add(create(inner, actor));
        }
        if (req.manpower() != null) {
            for (var mp : req.manpower()) {
                var inner = new CreateQuotationRequest(
                        req.siteId(), req.workPeriodStart(), req.workPeriodEnd(),
                        QuotationRequestType.MANPOWER,
                        null, mp.role(),
                        mp.specText(),
                        mp.proposedDailyRate(), mp.proposedMonthlyRate(),
                        mp.count(), req.notes(),
                        req.onBehalfOfBpCompanyId(),
                        bundleId,
                        mp.targets().stream()
                                .map(t -> new CreateQuotationRequest.TargetInput(
                                        t.supplierCompanyId(), null, t.personId()))
                                .toList()
                );
                result.add(create(inner, actor));
            }
        }

        // 다온톡 알림톡 — 장비 포함 묶음이면 수신번호로 SJR_254094 발송
        sendQuotationAlimtalk(req, actor);

        return result;
    }

    public QuotationRequest create(CreateQuotationRequest req, AuthenticatedUser actor) {
        ensureBpOrAdmin(actor);
        if (actor.role() == Role.ADMIN && req.onBehalfOfBpCompanyId() == null) {
            throw ApiException.badRequest("ON_BEHALF_REQUIRED",
                    "ADMIN 가 견적 요청 시 onBehalfOfBpCompanyId 필수");
        }

        if (req.workPeriodStart().isAfter(req.workPeriodEnd())) {
            throw ApiException.badRequest("INVALID_PERIOD", "작업 시작일이 종료일보다 앞서야 합니다");
        }
        if (req.targets() == null || req.targets().isEmpty()) {
            throw ApiException.badRequest("TARGETS_REQUIRED", "최소 1개 공급사/자원 선택이 필요합니다");
        }

        QuotationRequestType type = req.requestType() != null ? req.requestType() : QuotationRequestType.EQUIPMENT;
        if (type == QuotationRequestType.EQUIPMENT && req.equipmentCategory() == null) {
            throw ApiException.badRequest("EQUIPMENT_CATEGORY_REQUIRED", "장비 견적은 equipmentCategory 필수");
        }
        if (type == QuotationRequestType.MANPOWER && req.manpowerRole() == null) {
            throw ApiException.badRequest("MANPOWER_ROLE_REQUIRED", "인력 견적은 manpowerRole 필수");
        }

        Long onBehalfOf = (actor.role() == Role.ADMIN) ? req.onBehalfOfBpCompanyId() : null;
        Long bpCompanyId = onBehalfOf != null ? onBehalfOf : actor.companyId();

        QuotationRequest qr = QuotationRequest.builder()
                .siteId(null)
                .requestedByUserId(actor.id())
                .onBehalfOfBpCompanyId(onBehalfOf)
                .workPeriodStart(req.workPeriodStart())
                .workPeriodEnd(req.workPeriodEnd())
                .requestType(type)
                .equipmentCategory(type == QuotationRequestType.EQUIPMENT ? req.equipmentCategory() : null)
                .manpowerRole(type == QuotationRequestType.MANPOWER ? req.manpowerRole() : null)
                .specText(req.specText())
                .proposedDailyRate(req.proposedDailyRate())
                .proposedMonthlyRate(req.proposedMonthlyRate())
                .count(req.count())
                .notes(req.notes())
                .bundleId(req.bundleId())
                .mode(QuotationMode.TARGETED)
                .bpCompanyId(bpCompanyId)
                .build();
        requests.save(qr);

        // targets — 중복 (same supplier+resource) 자동 제거. site 무관 — 자원/공급사 매칭만 검증.
        Set<String> seen = new HashSet<>();
        Set<Long> notifiedSuppliers = new HashSet<>();
        for (var t : req.targets()) {
            String dedupKey = t.supplierCompanyId() + ":eq" + (t.equipmentId() == null ? "" : t.equipmentId())
                    + ":p" + (t.personId() == null ? "" : t.personId());
            if (!seen.add(dedupKey)) continue;
            if (type == QuotationRequestType.EQUIPMENT && t.equipmentId() != null) {
                Equipment eq = equipmentRepo.findById(t.equipmentId())
                        .orElseThrow(() -> ApiException.badRequest("EQUIPMENT_NOT_FOUND",
                                "장비 " + t.equipmentId() + " 없음"));
                if (!eq.getSupplierId().equals(t.supplierCompanyId())) {
                    throw ApiException.badRequest("EQUIPMENT_SUPPLIER_MISMATCH",
                            "장비 " + eq.getId() + " 는 지정 공급사 소속이 아닙니다");
                }
                if (eq.getCategory() != req.equipmentCategory()) {
                    throw ApiException.badRequest("EQUIPMENT_CATEGORY_MISMATCH",
                            "장비 " + eq.getId() + " 카테고리 (" + eq.getCategory()
                                    + ") 가 요청 카테고리 (" + req.equipmentCategory() + ") 와 다릅니다");
                }
            }
            if (type == QuotationRequestType.MANPOWER && t.personId() != null) {
                Person p = personRepo.findById(t.personId())
                        .orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND",
                                "인원 " + t.personId() + " 없음"));
                if (!p.getSupplierId().equals(t.supplierCompanyId())) {
                    throw ApiException.badRequest("PERSON_SUPPLIER_MISMATCH",
                            "인원 " + p.getId() + " 는 지정 공급사 소속이 아닙니다");
                }
                if (p.getRoles() == null || !p.getRoles().contains(req.manpowerRole())) {
                    throw ApiException.badRequest("PERSON_ROLE_MISMATCH",
                            "인원 " + p.getId() + " 에 요청 역할 (" + req.manpowerRole() + ") 없음");
                }
            }
            QuotationRequestTarget tgt = QuotationRequestTarget.builder()
                    .requestId(qr.getId())
                    .supplierCompanyId(t.supplierCompanyId())
                    .equipmentId(type == QuotationRequestType.EQUIPMENT ? t.equipmentId() : null)
                    .personId(type == QuotationRequestType.MANPOWER ? t.personId() : null)
                    .build();
            targets.save(tgt);
            notifiedSuppliers.add(t.supplierCompanyId());
        }

        String titleLabel = (type == QuotationRequestType.MANPOWER) ? "인력 견적 요청 수신" : "장비 견적 요청 수신";
        String siteName = qr.getSiteId() != null
                ? sites.findById(qr.getSiteId()).map(Site::getName).orElse(null) : null;
        String label = NotificationLabels.quotationLabel(qr, siteName);
        Company bpCompany = bpCompanyId != null ? companies.findById(bpCompanyId).orElse(null) : null;
        String bpLabel = bpCompany != null ? bpCompany.getName() : "BP";
        for (Long sid : notifiedSuppliers) {
            notifications.sendToCompany(sid,
                    NotificationType.QUOTATION_RECEIVED,
                    titleLabel,
                    bpLabel + " — " + label + " 견적 요청이 도착했습니다",
                    "QUOTATION_REQUEST", qr.getId(), null);
        }
        String resourceLabel = (type == QuotationRequestType.MANPOWER)
                ? NotificationLabels.personRole(req.manpowerRole())
                : NotificationLabels.equipmentCategory(req.equipmentCategory());

        auditLog.record(actor, AuditAction.QUOTATION_CREATED, AuditTargetType.QUOTATION_REQUEST,
                qr.getId(), bpCompanyId, null,
                null,
                "{\"type\":\"" + type.name() + "\",\"resource\":\"" + resourceLabel
                        + "\",\"targets\":" + notifiedSuppliers.size() + "}");
        return qr;
    }

    // ──────────────────────────────────────────────────────────────────────
    // V33: 공개입찰 (site 없이 시작, ClientOrg + workLocationText 옵션)
    // ──────────────────────────────────────────────────────────────────────

    public QuotationRequest createOpenBid(com.skep.quotation.dto.CreateOpenBidRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("REQUEST_DENIED", "BP/ADMIN 만 견적 발송 가능합니다");
        }
        if (req.workPeriodStart() == null || req.workPeriodEnd() == null
                || req.workPeriodStart().isAfter(req.workPeriodEnd())) {
            throw ApiException.badRequest("INVALID_PERIOD", "작업 기간이 올바르지 않습니다");
        }
        QuotationRequestType type = req.requestType() != null ? req.requestType() : QuotationRequestType.EQUIPMENT;
        if (type == QuotationRequestType.EQUIPMENT && req.equipmentCategory() == null) {
            throw ApiException.badRequest("EQUIPMENT_CATEGORY_REQUIRED", "장비 카테고리 필수");
        }
        if (type == QuotationRequestType.MANPOWER && req.manpowerRole() == null) {
            throw ApiException.badRequest("MANPOWER_ROLE_REQUIRED", "인력 역할 필수");
        }

        Long onBehalf = actor.role() == Role.ADMIN ? req.onBehalfOfBpCompanyId() : null;
        // V35: BP 본인 발신은 자기 회사. ADMIN onBehalfOf 는 그 BP 회사.
        Long bpCompanyId = onBehalf != null ? onBehalf : actor.companyId();

        QuotationRequest qr = QuotationRequest.builder()
                .siteId(null)
                .requestedByUserId(actor.id())
                .onBehalfOfBpCompanyId(onBehalf)
                .workPeriodStart(req.workPeriodStart())
                .workPeriodEnd(req.workPeriodEnd())
                .requestType(type)
                .equipmentCategory(type == QuotationRequestType.EQUIPMENT ? req.equipmentCategory() : null)
                .manpowerRole(type == QuotationRequestType.MANPOWER ? req.manpowerRole() : null)
                .specText(req.specText())
                .proposedDailyRate(req.proposedDailyRate())
                .proposedMonthlyRate(req.proposedMonthlyRate())
                .count(req.count() != null ? req.count() : 1)
                .notes(req.notes())
                .mode(QuotationMode.OPEN_BID)
                .clientOrgId(req.clientOrgId())
                .workLocationText(req.workLocationText())
                .bpCompanyId(bpCompanyId)
                .build();
        requests.save(qr);

        auditLog.record(actor, AuditAction.QUOTATION_CREATED, AuditTargetType.QUOTATION_REQUEST,
                qr.getId(),
                onBehalf != null ? onBehalf : actor.companyId(),
                null, null,
                "{\"mode\":\"OPEN_BID\",\"type\":\"" + type.name() + "\"}");

        // 외부 이메일 수신자에게 알림 메일 — 협업 공급사 외 추가 발송용
        if (req.emailRecipients() != null && !req.emailRecipients().isBlank()) {
            sendOpenBidNotificationEmails(qr, req.emailRecipients(), type);
        }
        return qr;
    }

    private void sendOpenBidNotificationEmails(QuotationRequest qr, String csvOrLines, QuotationRequestType type) {
        var sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailFrom == null || mailFrom.isBlank()) return;
        String[] addrs = csvOrLines.split("[,\\s\\n]+");
        String catLabel = qr.getEquipmentCategory() != null
                ? equipmentCategoryKo(qr.getEquipmentCategory().name()) : "";
        String roleLabel = qr.getManpowerRole() != null ? qr.getManpowerRole().name() : "";
        String resource = type == QuotationRequestType.MANPOWER ? roleLabel : catLabel;
        String period = qr.getWorkPeriodStart() + " ~ " + qr.getWorkPeriodEnd();
        String location = qr.getWorkLocationText() != null ? qr.getWorkLocationText() : "(현장 미정)";
        String body = "공개입찰 견적 안내\n\n"
                + "자원: " + resource + "\n"
                + "수량: " + qr.getCount() + "\n"
                + "기간: " + period + "\n"
                + "위치: " + location + "\n"
                + (qr.getSpecText() != null ? "스펙: " + qr.getSpecText() + "\n" : "")
                + (qr.getNotes() != null ? "메모: " + qr.getNotes() + "\n" : "")
                + "\nskep 공개입찰 게시판에서 응찰 가능합니다: https://skep.on1.kr/quotations/open-bids\n";
        for (String to : addrs) {
            to = to.trim();
            if (to.isEmpty() || !to.contains("@")) continue;
            try {
                org.springframework.mail.SimpleMailMessage msg = new org.springframework.mail.SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(to);
                msg.setSubject("[skep] 공개입찰 견적 안내 — " + resource);
                msg.setText(body);
                sender.send(msg);
            } catch (Exception e) {
                // 실패해도 견적 생성은 유지
            }
        }
    }

    private static String equipmentCategoryKo(String cat) {
        return switch (cat) {
            case "AERIAL_LIFT" -> "고소작업대";
            case "CRANE" -> "크레인";
            case "EXCAVATOR" -> "굴착기";
            case "BULLDOZER" -> "불도저";
            case "FORKLIFT" -> "지게차";
            case "LOADER" -> "로더";
            case "PUMP_TRUCK" -> "펌프카";
            case "DUMP_TRUCK" -> "덤프트럭";
            default -> cat;
        };
    }

    /** 공개입찰 게시판 — 공급사 카테고리 매칭 자동 필터. */
    @Transactional(readOnly = true)
    public List<QuotationRequestResponse> listOpenBids(AuthenticatedUser actor) {
        java.util.List<QuotationRequest> all = requests.findAllByOrderByIdDesc().stream()
                .filter(r -> r.getMode() == QuotationMode.OPEN_BID
                        && r.getStatus() == QuotationStatus.SENT)
                .toList();
        if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            all = all.stream().filter(r -> r.getRequestType() == QuotationRequestType.EQUIPMENT).toList();
        } else if (actor.role() == Role.MANPOWER_SUPPLIER) {
            all = all.stream().filter(r -> r.getRequestType() == QuotationRequestType.MANPOWER).toList();
        }
        // ADMIN/BP 본인 작성한 견적도 확인하려면 전체.
        Lookups lk = buildLookups(all, false);
        return all.stream().map(qr -> toResponse(qr, false, null, lk)).toList();
    }

    // ──────────────────────────────────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuotationRequestResponse> list(AuthenticatedUser actor) {
        List<QuotationRequest> rows;
        if (actor.role() == Role.ADMIN) {
            rows = requests.findAllByOrderByIdDesc();
        } else if (actor.role() == Role.BP) {
            requireCompany(actor);
            // V35: bp_company_id 직접 컬럼으로 TARGETED + OPEN_BID 통일.
            rows = requests.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            requireCompany(actor);
            // 공급사 자기에게 target 이 있는 견적만
            List<Long> myTargetReqIds = targets.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
                    .map(QuotationRequestTarget::getRequestId).distinct().toList();
            rows = myTargetReqIds.isEmpty() ? List.of() : requests.findAllById(myTargetReqIds).stream()
                    .sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList();
        } else {
            return List.of();
        }
        Lookups lk = buildLookups(rows, false);
        return rows.stream().map(qr -> toResponse(qr, false, null, lk)).toList();
    }

    @Transactional(readOnly = true)
    public QuotationRequestResponse get(Long id, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(id)
                .orElseThrow(() -> ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + id + " 없음"));
        ensureCanView(actor, qr);
        return toResponseDetail(qr, actor);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 응답 (공급사)
    // ──────────────────────────────────────────────────────────────────────

    public QuotationRequestResponse respond(Long requestId, Long targetId,
                                              RespondQuotationTargetRequest req,
                                              AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + requestId + " 없음"));
        QuotationRequestTarget tgt = targets.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("TARGET_NOT_FOUND", "target " + targetId + " 없음"));
        if (!tgt.getRequestId().equals(qr.getId())) {
            throw ApiException.badRequest("TARGET_REQUEST_MISMATCH", "target 이 해당 견적 소속이 아닙니다");
        }
        // 권한: 자기 공급사 target 만 (EQUIPMENT_SUPPLIER 또는 MANPOWER_SUPPLIER)
        boolean isOwnSupplier = (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && tgt.getSupplierCompanyId().equals(actor.companyId());
        if (actor.role() != Role.ADMIN && !isOwnSupplier) {
            throw ApiException.forbidden("RESPOND_DENIED", "자기 회사 target 만 응답 가능합니다");
        }
        if (tgt.getStatus() != QuotationTargetStatus.PENDING) {
            throw ApiException.badRequest("TARGET_NOT_PENDING",
                    "이미 처리된 target 입니다 (현재: " + tgt.getStatus() + ")");
        }
        if (qr.getStatus() != QuotationStatus.SENT) {
            throw ApiException.badRequest("REQUEST_NOT_OPEN",
                    "견적이 더 이상 응답 받을 수 없습니다 (상태: " + qr.getStatus() + ")");
        }

        if (Boolean.TRUE.equals(req.accept())) {
            tgt.markAccepted(actor.id(), req.note());
        } else {
            tgt.markRejected(actor.id(), req.note());
        }

        // BP/ADMIN 에게 알림 (BP 회사로 broadcast — qr.bp_company_id 직접 사용. site 는 견적 시점에 없음)
        Long bpCompanyId = qr.getBpCompanyId();
        Company supplier = companies.findById(tgt.getSupplierCompanyId()).orElse(null);
        String supplierName = supplier != null ? supplier.getName() : ("회사 #" + tgt.getSupplierCompanyId());
        if (bpCompanyId != null) {
            String label2 = NotificationLabels.quotationLabel(qr, null);
            notifications.sendToCompany(bpCompanyId,
                    NotificationType.QUOTATION_RESPONDED,
                    "견적 응답 수신",
                    supplierName + " 가 [" + label2 + "] 견적에 "
                            + (Boolean.TRUE.equals(req.accept()) ? "수락" : "거부") + " 응답했습니다",
                    "QUOTATION_REQUEST", qr.getId(), qr.getSiteId());
        }

        auditLog.record(actor, AuditAction.QUOTATION_RESPONDED, AuditTargetType.QUOTATION_REQUEST,
                qr.getId(), bpCompanyId, qr.getSiteId(),
                null,
                "{\"target_id\":" + tgt.getId()
                        + ",\"accept\":" + Boolean.TRUE.equals(req.accept()) + "}");
        return toResponseDetail(qr, actor);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 최종 수락 (BP/ADMIN)
    // ──────────────────────────────────────────────────────────────────────

    public QuotationRequestResponse finalize(Long requestId, Long targetId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + requestId + " 없음"));
        QuotationRequestTarget tgt = targets.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("TARGET_NOT_FOUND", "target " + targetId + " 없음"));
        if (!tgt.getRequestId().equals(qr.getId())) {
            throw ApiException.badRequest("TARGET_REQUEST_MISMATCH", "target 이 해당 견적 소속이 아닙니다");
        }
        ensureCanFinalize(actor, qr);

        if (tgt.getStatus() != QuotationTargetStatus.ACCEPTED) {
            throw ApiException.badRequest("TARGET_NOT_ACCEPTED",
                    "공급사 ACCEPTED 상태에서만 최종 수락 가능합니다 (현재: " + tgt.getStatus() + ")");
        }

        // Site-B: 선정은 target 만 FINAL_ACCEPTED. 작업계획서/자원 첨부는 별도 단계(Site-D)에서.
        tgt.markFinalAccepted(actor.id(), null, null);

        long openCount = targets.countByRequestIdAndStatus(qr.getId(), QuotationTargetStatus.PENDING)
                + targets.countByRequestIdAndStatus(qr.getId(), QuotationTargetStatus.ACCEPTED);
        if (openCount == 0) qr.markClosed();

        String finalSiteName = qr.getSiteId() != null
                ? sites.findById(qr.getSiteId()).map(Site::getName).orElse(null) : null;
        String finalLabel = NotificationLabels.quotationLabel(qr, finalSiteName);
        notifications.sendToCompany(tgt.getSupplierCompanyId(),
                NotificationType.QUOTATION_FINALIZED,
                "견적 최종 수락",
                "[" + finalLabel + "] 견적이 최종 수락되었습니다. 작업계획서는 BP가 별도로 작성합니다.",
                "QUOTATION_REQUEST", qr.getId(), qr.getSiteId());

        auditLog.record(actor, AuditAction.QUOTATION_FINALIZED, AuditTargetType.QUOTATION_REQUEST,
                qr.getId(), qr.getBpCompanyId(), qr.getSiteId(),
                null,
                "{\"target_id\":" + tgt.getId() + "}");
        return toResponseDetail(qr, actor);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Bundle (현장 묶음) — 사용자 관점 "견적 1건"
    // ──────────────────────────────────────────────────────────────────────

    /** 사용자 접근 가능한 묶음 목록. 같은 bundle_id 끼리 그룹핑. NULL bundle_id 는 단건 묶음으로 처리. */
    @Transactional(readOnly = true)
    public java.util.List<com.skep.quotation.dto.QuotationBundleResponse> listBundles(AuthenticatedUser actor) {
        // 권한 적용된 raw entity 조회 (detail 변환 필요 — targets 포함)
        java.util.List<QuotationRequest> rows;
        if (actor.role() == Role.ADMIN) {
            rows = requests.findAllByOrderByIdDesc();
        } else if (actor.role() == Role.BP) {
            if (actor.companyId() == null) return java.util.List.of();
            // V35: bp_company_id 직접 컬럼으로 TARGETED + OPEN_BID 통일.
            rows = requests.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() == null) return java.util.List.of();
            java.util.List<Long> myReqIds = targets.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
                    .map(QuotationRequestTarget::getRequestId).distinct().toList();
            rows = myReqIds.isEmpty() ? java.util.List.of() : requests.findAllById(myReqIds).stream()
                    .sorted((a, b) -> Long.compare(b.getId(), a.getId())).toList();
        } else {
            return java.util.List.of();
        }
        // detail (targets 포함) 변환
        Lookups lk = buildLookups(rows, true);
        java.util.List<com.skep.quotation.dto.QuotationRequestResponse> all = rows.stream()
                .map(qr -> toResponse(qr, true, actor, lk)).toList();
        java.util.Map<String, java.util.List<com.skep.quotation.dto.QuotationRequestResponse>> grouped =
                new java.util.LinkedHashMap<>();
        for (var qr : all) {
            String key = qr.bundleId() != null ? qr.bundleId().toString() : ("solo-" + qr.id());
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(qr);
        }
        return grouped.values().stream().map(this::toBundleResponse).toList();
    }

    /** 단일 묶음 조회. 묶음 내 자기에게 권한 있는 row 만 필터하여 반환. */
    @Transactional(readOnly = true)
    public com.skep.quotation.dto.QuotationBundleResponse getBundle(java.util.UUID bundleId, AuthenticatedUser actor) {
        var rows = requests.findByBundleIdOrderByIdAsc(bundleId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("BUNDLE_NOT_FOUND", "묶음 " + bundleId + " 없음");
        }
        // 묶음 내 자기 권한 있는 row 만 — 공급사면 자기 target 있는 row 만, BP 면 자기 사이트 row 만.
        var visible = rows.stream().filter(qr -> hasViewAccess(actor, qr)).toList();
        if (visible.isEmpty()) {
            throw ApiException.forbidden("VIEW_DENIED", "이 묶음에 대한 조회 권한이 없습니다");
        }
        var items = visible.stream().map(qr -> toResponseDetail(qr, actor)).toList();
        return toBundleResponse(items);
    }

    /** ensureCanView 의 throw 안 하는 버전. */
    private boolean hasViewAccess(AuthenticatedUser actor, QuotationRequest qr) {
        if (actor.role() == Role.ADMIN) return true;
        if (actor.role() == Role.BP) {
            // V35: bp_company_id 직접 비교 — TARGETED/OPEN_BID 통일.
            return actor.companyId() != null && actor.companyId().equals(qr.getBpCompanyId());
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() == null) return false;
            if (qr.getMode() == QuotationMode.OPEN_BID) return true;
            return targets.findByRequestIdOrderByIdAsc(qr.getId()).stream()
                    .anyMatch(t -> t.getSupplierCompanyId().equals(actor.companyId()));
        }
        return false;
    }

    public void cancelBundle(java.util.UUID bundleId, AuthenticatedUser actor) {
        var rows = requests.findByBundleIdOrderByIdAsc(bundleId);
        if (rows.isEmpty()) throw ApiException.notFound("BUNDLE_NOT_FOUND", "묶음 없음");
        for (var qr : rows) {
            if (qr.getStatus() == QuotationStatus.SENT) {
                cancel(qr.getId(), actor);
            }
        }
    }

    public void deleteBundle(java.util.UUID bundleId, AuthenticatedUser actor) {
        var rows = requests.findByBundleIdOrderByIdAsc(bundleId);
        if (rows.isEmpty()) throw ApiException.notFound("BUNDLE_NOT_FOUND", "묶음 없음");
        for (var qr : rows) {
            delete(qr.getId(), actor);
        }
    }

    private com.skep.quotation.dto.QuotationBundleResponse toBundleResponse(
            java.util.List<com.skep.quotation.dto.QuotationRequestResponse> items) {
        if (items.isEmpty()) return null;
        var head = items.get(0);
        // 집계
        int totalT = 0, resp = 0, acc = 0, fin = 0;
        int propAll = 0, propPending = 0;
        Long firstWpId = null;
        for (var it : items) {
            // OPEN_BID 견적은 quotation_proposals 별도 카운트 (target 0 이라 X/Y 의미 X).
            if (it.mode() == QuotationMode.OPEN_BID) {
                propAll += (int) proposals.countByRequestId(it.id());
                propPending += (int) proposals.countByRequestIdAndStatus(it.id(), QuotationProposalStatus.SUBMITTED);
                propPending += (int) proposals.countByRequestIdAndStatus(it.id(), QuotationProposalStatus.PENDING_REVIEW);
            }
            if (it.targets() == null) continue;
            for (var t : it.targets()) {
                totalT++;
                if (t.status() != QuotationTargetStatus.PENDING) resp++;
                if (t.status() == QuotationTargetStatus.ACCEPTED || t.status() == QuotationTargetStatus.FINAL_ACCEPTED) acc++;
                if (t.status() == QuotationTargetStatus.FINAL_ACCEPTED) {
                    fin++;
                    if (firstWpId == null) firstWpId = t.finalizedToWorkPlanId();
                }
            }
        }
        // 집계 status — 모두 CLOSED 면 CLOSED, 모두 CANCELLED 면 CANCELLED, 그 외 SENT
        boolean anySent = items.stream().anyMatch(i -> i.status() == QuotationStatus.SENT);
        boolean allCancelled = items.stream().allMatch(i -> i.status() == QuotationStatus.CANCELLED);
        boolean allClosed = items.stream().allMatch(i -> i.status() == QuotationStatus.CLOSED);
        QuotationStatus agg = anySent ? QuotationStatus.SENT
                : (allCancelled ? QuotationStatus.CANCELLED : (allClosed ? QuotationStatus.CLOSED : QuotationStatus.SENT));
        // 묶음 bundle_id — 모든 item 의 bundle_id 가 같다고 가정 (또는 NULL이면 -solo-)
        java.util.UUID bid = head.bundleId();
        return new com.skep.quotation.dto.QuotationBundleResponse(
                bid,
                head.siteId(), head.siteName(),
                head.bpCompanyId(), head.bpCompanyName(),
                head.requestedByUserId(), head.requestedByUserName(),
                head.onBehalfOfBpCompanyId(),
                head.workPeriodStart(), head.workPeriodEnd(),
                head.notes(),
                agg, totalT, resp, acc, fin, propAll, propPending, firstWpId,
                head.createdAt(), head.updatedAt(),
                items
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    // 취소
    // ──────────────────────────────────────────────────────────────────────

    /** BP/ADMIN 견적 완전 삭제 — targets/proposals 함께 제거. FINAL_ACCEPTED / ACCEPTED proposal 있으면 차단. */
    public void delete(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + requestId + " 없음"));
        ensureCanFinalize(actor, qr); // BP/ADMIN 만
        long finalized = targets.countByRequestIdAndStatus(qr.getId(), QuotationTargetStatus.FINAL_ACCEPTED);
        if (finalized > 0) {
            throw ApiException.badRequest("ALREADY_FINALIZED",
                    "이미 최종 선정된 견적은 삭제할 수 없습니다.");
        }
        // OPEN_BID: FINAL_ACCEPTED 제안이 있으면 차단 (이미 finalize_check 에서 막지만 이중 보호).
        if (qr.getMode() == QuotationMode.OPEN_BID) {
            long finalAcceptedProp = proposals.countByRequestIdAndStatus(qr.getId(), QuotationProposalStatus.FINAL_ACCEPTED);
            if (finalAcceptedProp > 0) {
                throw ApiException.badRequest("PROPOSAL_FINALIZED",
                        "이미 최종 선정된 제안이 있는 견적은 삭제할 수 없습니다.");
            }
            proposals.deleteByRequestId(qr.getId());
        }
        for (var t : targets.findByRequestIdOrderByIdAsc(qr.getId())) {
            targets.delete(t);
        }
        requests.delete(qr);
        auditLog.record(actor, AuditAction.QUOTATION_CANCELLED, AuditTargetType.QUOTATION_REQUEST,
                qr.getId(), qr.getBpCompanyId(), qr.getSiteId(), null, "{\"action\":\"delete\"}");
    }

    public QuotationRequestResponse cancel(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + requestId + " 없음"));
        ensureCanFinalize(actor, qr); // 취소는 BP/ADMIN 만 (finalize 와 같은 권한)

        if (qr.getStatus() == QuotationStatus.CLOSED || qr.getStatus() == QuotationStatus.CANCELLED) {
            return toResponseDetail(qr, actor);
        }
        qr.markCancelled();

        auditLog.record(actor, AuditAction.QUOTATION_CANCELLED, AuditTargetType.QUOTATION_REQUEST,
                qr.getId(), qr.getBpCompanyId(), qr.getSiteId(), null, null);
        return toResponseDetail(qr, actor);
    }

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private static void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        }
    }

    /** 최종 수락 권한 — BP self 또는 ADMIN. site-free: qr.bp_company_id 직접 비교. */
    private void ensureCanFinalize(AuthenticatedUser actor, QuotationRequest qr) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            requireCompany(actor);
            if (qr.getBpCompanyId() == null || !qr.getBpCompanyId().equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_BP",
                        "본인 회사 견적만 최종 수락 가능합니다");
            }
            return;
        }
        throw ApiException.forbidden("FINALIZE_DENIED", "최종 수락 권한이 없습니다");
    }

    /** 견적 조회 권한 — ADMIN, BP self, 자기에게 target 있는 EQUIPMENT_SUPPLIER. */
    private void ensureCanView(AuthenticatedUser actor, QuotationRequest qr) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            // V35: bp_company_id 직접 비교 — TARGETED/OPEN_BID 통일.
            if (actor.companyId() != null && actor.companyId().equals(qr.getBpCompanyId())) return;
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
            }
            // OPEN_BID: 모든 공급사가 게시판에서 조회 가능.
            if (qr.getMode() == QuotationMode.OPEN_BID) return;
            // TARGETED: 자기 회사가 target 인 경우만.
            boolean hasTarget = targets.findByRequestIdOrderByIdAsc(qr.getId()).stream()
                    .anyMatch(t -> t.getSupplierCompanyId().equals(actor.companyId()));
            if (hasTarget) return;
        }
        throw ApiException.forbidden("VIEW_DENIED", "견적 조회 권한이 없습니다");
    }

    private QuotationRequestResponse toResponseDetail(QuotationRequest qr, AuthenticatedUser actor) {
        return toResponse(qr, /*withTargets*/ true, actor, buildLookups(List.of(qr), true));
    }

    private QuotationRequestResponse toResponse(QuotationRequest qr, boolean withTargets, AuthenticatedUser actor,
                                                Lookups lk) {
        // OPEN_BID 모드는 site 없을 수 있음.
        Site site = qr.getSiteId() != null ? lk.sites().get(qr.getSiteId()) : null;
        // V35: bp_company_id 직접 컬럼.
        Long bpCompanyId = qr.getBpCompanyId();
        Company bpCompany = bpCompanyId != null ? lk.companies().get(bpCompanyId) : null;
        String bpName = bpCompany != null ? bpCompany.getName() : null;
        User reqUser = lk.users().get(qr.getRequestedByUserId());
        String reqUserName = reqUser != null ? reqUser.getName() : null;
        ClientOrg clientOrg = qr.getClientOrgId() != null ? lk.clientOrgs().get(qr.getClientOrgId()) : null;
        String clientOrgName = clientOrg != null ? clientOrg.getName() : null;

        List<QuotationRequestResponse.TargetItem> targetItems = List.of();
        if (withTargets) {
            List<QuotationRequestTarget> rows = lk.targetsByRequestId().getOrDefault(qr.getId(), List.of());
            // P2: 공급사 시점이면 자기 회사 target 만 노출 (EQUIPMENT + MANPOWER 모두)
            if (actor != null && (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)) {
                rows = rows.stream()
                        .filter(t -> t.getSupplierCompanyId().equals(actor.companyId()))
                        .toList();
            }
            targetItems = rows.stream().map(t -> {
                Company supplier = lk.companies().get(t.getSupplierCompanyId());
                String supplierName = supplier != null && supplier.getName() != null
                        ? supplier.getName() : ("회사 #" + t.getSupplierCompanyId());
                String eqLabel = null;
                if (t.getEquipmentId() != null) {
                    Equipment eq = lk.equipment().get(t.getEquipmentId());
                    if (eq != null) {
                        eqLabel = eq.getVehicleNo() != null ? eq.getVehicleNo()
                                : (eq.getModel() != null ? eq.getModel() : "장비 #" + eq.getId());
                    }
                }
                String personLabel = null;
                if (t.getPersonId() != null) {
                    Person p = lk.persons().get(t.getPersonId());
                    if (p != null) personLabel = p.getName();
                }
                return new QuotationRequestResponse.TargetItem(
                        t.getId(), t.getSupplierCompanyId(), supplierName,
                        t.getEquipmentId(), eqLabel,
                        t.getPersonId(), personLabel,
                        t.getStatus(),
                        t.getRespondedByUserId(), t.getRespondedAt(), t.getResponseNote(),
                        t.getFinalizedByUserId(), t.getFinalizedAt(),
                        t.getFinalizedToWorkPlanId(), t.getFinalizedToWpeId());
            }).toList();
        }

        return new QuotationRequestResponse(
                qr.getId(), qr.getSiteId(), site != null ? site.getName() : null,
                bpCompanyId, bpName,
                qr.getRequestedByUserId(), reqUserName,
                qr.getOnBehalfOfBpCompanyId(),
                qr.getWorkPeriodStart(), qr.getWorkPeriodEnd(),
                qr.getRequestType(),
                qr.getEquipmentCategory(),
                qr.getManpowerRole(),
                qr.getSpecText(),
                qr.getProposedDailyRate(), qr.getProposedMonthlyRate(),
                qr.getCount(), qr.getNotes(),
                qr.getStatus(),
                qr.getBundleId(),
                qr.getMode(), qr.getClientOrgId(), clientOrgName, qr.getWorkLocationText(),
                qr.getCreatedAt(), qr.getUpdatedAt(),
                targetItems
        );
    }

    /** toResponse 가 참조하는 엔티티/타깃을 목록 단위로 미리 담아두는 배치 조회 컨텍스트. */
    private record Lookups(
            Map<Long, Site> sites,
            Map<Long, Company> companies,
            Map<Long, User> users,
            Map<Long, ClientOrg> clientOrgs,
            Map<Long, Equipment> equipment,
            Map<Long, Person> persons,
            Map<Long, List<QuotationRequestTarget>> targetsByRequestId) {}

    /** 주어진 견적들이 toResponse 에서 조회할 id 를 모아 findAllById 로 일괄 조회 (행별 findById N+1 제거). */
    private Lookups buildLookups(List<QuotationRequest> qrs, boolean withTargets) {
        Set<Long> siteIds = new HashSet<>();
        Set<Long> companyIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        Set<Long> clientOrgIds = new HashSet<>();
        for (QuotationRequest qr : qrs) {
            if (qr.getSiteId() != null) siteIds.add(qr.getSiteId());
            if (qr.getBpCompanyId() != null) companyIds.add(qr.getBpCompanyId());
            if (qr.getRequestedByUserId() != null) userIds.add(qr.getRequestedByUserId());
            if (qr.getClientOrgId() != null) clientOrgIds.add(qr.getClientOrgId());
        }

        Map<Long, List<QuotationRequestTarget>> targetsByReq = new LinkedHashMap<>();
        Set<Long> equipmentIds = new HashSet<>();
        Set<Long> personIds = new HashSet<>();
        if (withTargets && !qrs.isEmpty()) {
            List<Long> reqIds = qrs.stream().map(QuotationRequest::getId).toList();
            for (QuotationRequestTarget t : targets.findByRequestIdInOrderByIdAsc(reqIds)) {
                targetsByReq.computeIfAbsent(t.getRequestId(), k -> new ArrayList<>()).add(t);
                if (t.getSupplierCompanyId() != null) companyIds.add(t.getSupplierCompanyId());
                if (t.getEquipmentId() != null) equipmentIds.add(t.getEquipmentId());
                if (t.getPersonId() != null) personIds.add(t.getPersonId());
            }
        }

        return new Lookups(
                mapById(sites.findAllById(siteIds), Site::getId),
                mapById(companies.findAllById(companyIds), Company::getId),
                mapById(users.findAllById(userIds), User::getId),
                mapById(clientOrgs.findAllById(clientOrgIds), ClientOrg::getId),
                mapById(equipmentRepo.findAllById(equipmentIds), Equipment::getId),
                mapById(personRepo.findAllById(personIds), Person::getId),
                targetsByReq);
    }

    private static <T> Map<Long, T> mapById(List<T> rows, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>();
        for (T row : rows) m.put(idFn.apply(row), row);
        return m;
    }

    // ── 다온톡 알림톡 (장비 투입 요청 SJR_254094) ───────────────────────────
    /** 수신번호 있고 장비 포함 묶음이면 SJR_254094 발송. 변수는 첫 장비 타깃 기준 요약. (AlimTalkService 가 실패 swallow) */
    private void sendQuotationAlimtalk(CreateQuotationBundleRequest req, AuthenticatedUser actor) {
        if (req.alimtalkPhones() == null || req.alimtalkPhones().isEmpty()) return;
        var eq = req.equipment();
        if (eq == null || eq.targets() == null || eq.targets().isEmpty()) return;  // 장비 없으면 템플릿 부적합

        var firstTarget = eq.targets().get(0);
        Long bpCompanyId = req.onBehalfOfBpCompanyId() != null ? req.onBehalfOfBpCompanyId() : actor.companyId();

        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("업체명", atCompanyName(firstTarget.supplierCompanyId()));
        vars.put("BP사", atCompanyName(bpCompanyId));
        vars.put("현장명", atSiteName(req.siteId()));
        vars.put("장비명", atEquipmentName(firstTarget.equipmentId(), eq.category()));
        vars.put("이름", atOperatorName(req.manpower()));
        vars.put("요청기한", req.workPeriodStart() != null ? req.workPeriodStart().toString() : "미정");

        for (String phone : req.alimtalkPhones()) {
            alimTalk.send(phone, AlimTalkTemplate.EQUIPMENT_QUOTE, vars, actor.id());
        }
    }

    private String atCompanyName(Long companyId) {
        if (companyId == null) return "";
        return companies.findById(companyId).map(Company::getName).orElse("");
    }

    private String atSiteName(Long siteId) {
        if (siteId == null) return "-";
        return sites.findById(siteId).map(Site::getName).orElse("-");
    }

    private String atEquipmentName(Long equipmentId, EquipmentCategory category) {
        if (equipmentId != null) {
            var found = equipmentRepo.findById(equipmentId).orElse(null);
            if (found != null) {
                if (found.getModel() != null && !found.getModel().isBlank()) return found.getModel();
                if (found.getVehicleNo() != null && !found.getVehicleNo().isBlank()) return found.getVehicleNo();
            }
        }
        return category != null ? equipmentCategoryKo(category.name()) : "장비";
    }

    private String atOperatorName(java.util.List<CreateQuotationBundleRequest.ManpowerItem> manpower) {
        if (manpower == null) return "";
        for (var mp : manpower) {
            if (mp.targets() == null) continue;
            for (var t : mp.targets()) {
                var p = personRepo.findById(t.personId()).orElse(null);
                if (p != null) return p.getName();
            }
        }
        return "";
    }

    /** unused but keep — 향후 BP 컨텍스트 전환 UI 용. */
    @SuppressWarnings("unused")
    private LocalDate today() { return LocalDate.now(); }
}
