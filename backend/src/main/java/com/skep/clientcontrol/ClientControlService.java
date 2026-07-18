package com.skep.clientcontrol;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.clientcontrol.dto.ClientControlDtos.*;
import com.skep.clientorg.ClientOrg;
import com.skep.clientorg.ClientOrgRepository;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.DailyEquipmentInspection;
import com.skep.equipment.DailyEquipmentInspectionRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentAssignmentStatus;
import com.skep.equipment.EquipmentRepository;
import com.skep.legalinspection.LegalInspection;
import com.skep.legalinspection.LegalInspectionRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.FieldSafetyAlert;
import com.skep.safety.FieldSafetyAlertRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteParticipantType;
import com.skep.site.SiteRepository;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 원청(client_org) 통합 관제 허브 — 읽기전용 집계(§1.1).
 * 자원(장비/인원) 집계는 현장의 활성 작업계획서(SUBMITTED+)를 원천으로 삼아 BP 투입 현황(board)과 동일 기준.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientControlService {

    private static final List<WorkPlanStatus> ACTIVE_WP = List.of(
            WorkPlanStatus.SUBMITTED, WorkPlanStatus.APPROVED, WorkPlanStatus.IN_PROGRESS, WorkPlanStatus.DONE);

    private final UserRepository users;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final CompanyRepository companies;
    private final ClientOrgRepository clientOrgs;
    private final WorkPlanRepository workPlans;
    private final WorkPlanEquipmentRepository wpEquipments;
    private final WorkPlanPersonRepository wpPersons;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final AttendanceSessionRepository attendance;
    private final FieldSafetyAlertRepository safetyAlerts;
    private final DocumentRepository documents;
    private final DailyEquipmentInspectionRepository inspections;
    private final LegalInspectionRepository legalInspections;

    /** 내 원청에 연결된 현장 목록(요약 카드). */
    public List<ClientSiteSummary> listSites(AuthenticatedUser actor) {
        Long orgId = requireClientOrg(actor);
        List<Site> siteList = sites.findByClientOrgIdOrderByIdDesc(orgId);
        List<ClientSiteSummary> out = new ArrayList<>();
        for (Site site : siteList) {
            ResourceSet rs = resourceSet(site.getId());
            AttendanceSummary att = attendanceSummary(rs.personIds());
            long unresolved = safetyAlerts.findBySiteIdOrderByCreatedAtDesc(site.getId()).stream()
                    .filter(a -> !a.isResolved()).count();
            int activeParticipants = (int) participants.findBySiteIdOrderByIdDesc(site.getId()).stream()
                    .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE).count();
            out.add(new ClientSiteSummary(
                    site.getId(), site.getName(), site.getCode(),
                    companyName(site.getBpCompanyId()),
                    site.getStatus() != null ? site.getStatus().name() : null,
                    activeParticipants, att.deployedPersonCount(), att.currentlyCheckedIn(), unresolved));
        }
        return out;
    }

    /** 현장 통합 관제 상세. 타 원청 현장은 403. */
    public ClientSiteOverview overview(Long siteId, AuthenticatedUser actor) {
        Long orgId = requireClientOrg(actor);
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        if (!orgId.equals(site.getClientOrgId())) {
            throw ApiException.forbidden("CLIENT_SITE_DENIED", "내 원청 현장이 아닙니다");
        }

        ResourceSet rs = resourceSet(siteId);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 참여 공급사 (ACTIVE)
        List<SiteParticipant> activeParts = participants.findBySiteIdOrderByIdDesc(siteId).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE).toList();
        Map<Long, Company> compMap = companies.findAllById(
                        activeParts.stream().map(SiteParticipant::getCompanyId).distinct().toList()).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
        List<SupplierItem> suppliers = activeParts.stream()
                .map(p -> new SupplierItem(p.getCompanyId(),
                        compMap.containsKey(p.getCompanyId()) ? compMap.get(p.getCompanyId()).getName() : null,
                        participantTypeLabel(p.getParticipantType())))
                .toList();

        // 투입 장비 (상태별)
        List<Equipment> eqs = rs.equipmentIds().isEmpty() ? List.of() : equipments.findAllById(rs.equipmentIds());
        Map<Long, Equipment> eqMap = eqs.stream().collect(Collectors.toMap(Equipment::getId, Function.identity()));
        long assigned = eqs.stream().filter(e -> e.getAssignmentStatus() == EquipmentAssignmentStatus.ASSIGNED).count();
        long available = eqs.stream().filter(e -> e.getAssignmentStatus() == EquipmentAssignmentStatus.AVAILABLE).count();
        long broken = eqs.stream().filter(e -> e.getAssignmentStatus() == EquipmentAssignmentStatus.BROKEN).count();
        EquipmentStatusCount equipment = new EquipmentStatusCount(eqs.size(), assigned, available, broken);

        // 출근 + 혼잡도
        AttendanceSummary attendanceSummary = attendanceSummary(rs.personIds());
        Congestion congestion = congestion(rs.personIds(), today, now);

        // 일일점검 2트랙 — 조종원 일일점검 + S2′ 법정점검(안전점검원 NFC). 대상 = 배치 장비 동일.
        int inspectTarget = rs.equipmentIds().size();
        int inspectDone = rs.equipmentIds().isEmpty() ? 0 : (int) inspections
                .findByEquipmentIdInAndInspectDate(rs.equipmentIds(), today).stream()
                .map(DailyEquipmentInspection::getEquipmentId).distinct().count();
        int legalDone = rs.equipmentIds().isEmpty() ? 0 : (int) legalInspections
                .findByEquipmentIdInAndInspectDate(rs.equipmentIds(), today).stream()
                .map(LegalInspection::getEquipmentId).distinct().count();
        DailyInspection dailyInspection = new DailyInspection(inspectTarget, inspectDone, inspectTarget, legalDone);

        // 안전알림 (미해결)
        List<FieldSafetyAlert> unresolvedAlerts = safetyAlerts.findBySiteIdOrderByCreatedAtDesc(siteId).stream()
                .filter(a -> !a.isResolved()).toList();
        Map<Long, Person> personMap = loadPersons(rs.personIds(),
                unresolvedAlerts.stream().map(FieldSafetyAlert::getPersonId).toList());
        List<AlertItem> recentAlerts = unresolvedAlerts.stream().limit(8)
                .map(a -> new AlertItem(a.getId(), a.getKind(), a.getLevel(), a.getMessage(),
                        personName(personMap, a.getPersonId()), a.getCreatedAt(),
                        a.getSeverity(), a.getAcknowledgedAt(), a.getEscalatedAt()))
                .toList();

        // 만료 임박 서류 (D-30) — 현장 투입 자원 소유분.
        LocalDate maxDate = today.plusDays(30);
        List<Document> expiring = new ArrayList<>();
        if (!rs.equipmentIds().isEmpty()) {
            expiring.addAll(documents.findRiskyForOwners(OwnerType.EQUIPMENT, rs.equipmentIds(), maxDate));
        }
        if (!rs.personIds().isEmpty()) {
            expiring.addAll(documents.findRiskyForOwners(OwnerType.PERSON, rs.personIds(), maxDate));
        }
        // findRiskyForOwners 는 반려/검토도 포함 → 만료 임박(expiry_date 존재)만.
        List<Document> expiringOnly = expiring.stream()
                .filter(d -> d.getExpiryDate() != null && !d.getExpiryDate().isAfter(maxDate))
                .sorted(Comparator.comparing(Document::getExpiryDate))
                .toList();
        List<ExpiringItem> expiringItems = expiringOnly.stream().limit(10)
                .map(d -> new ExpiringItem(
                        d.getOwnerType().name(),
                        ownerLabel(d.getOwnerType(), d.getOwnerId(), eqMap, personMap),
                        d.getExpiryDate(),
                        ChronoUnit.DAYS.between(today, d.getExpiryDate())))
                .toList();

        String clientOrgName = clientOrgs.findById(orgId).map(ClientOrg::getName).orElse(null);

        return new ClientSiteOverview(
                site.getId(), site.getName(), site.getCode(), site.getAddress(),
                companyName(site.getBpCompanyId()), clientOrgName,
                site.getStatus() != null ? site.getStatus().name() : null,
                site.getStartDate(), site.getEndDate(),
                suppliers, equipment, attendanceSummary, congestion, dailyInspection,
                unresolvedAlerts.size(), recentAlerts,
                expiringOnly.size(), expiringItems);
    }

    // ── 내부 ──────────────────────────────────────────────

    private record ResourceSet(List<Long> equipmentIds, List<Long> personIds) {}

    /** 현장 활성 작업계획서 → 투입 장비/인원 id 집합. */
    private ResourceSet resourceSet(Long siteId) {
        List<WorkPlan> wps = workPlans.findBySiteIdAndStatusInOrderByIdDesc(siteId, ACTIVE_WP);
        if (wps.isEmpty()) return new ResourceSet(List.of(), List.of());
        List<Long> wpIds = wps.stream().map(WorkPlan::getId).toList();
        List<Long> eqIds = wpEquipments.findByWorkPlanIdIn(wpIds).stream()
                .map(WorkPlanEquipment::getEquipmentId).distinct().toList();
        List<Long> pIds = wpPersons.findByWorkPlanIdIn(wpIds).stream()
                .map(WorkPlanPerson::getPersonId).distinct().toList();
        return new ResourceSet(eqIds, pIds);
    }

    private AttendanceSummary attendanceSummary(List<Long> personIds) {
        if (personIds.isEmpty()) return new AttendanceSummary(0, 0, 0);
        LocalDate today = LocalDate.now();
        List<AttendanceSession> todaySessions = attendance
                .findByPersonIdInAndCheckInAtGreaterThanEqualOrderByCheckInAtDesc(personIds, today.atStartOfDay())
                .stream().filter(s -> s.getCheckInAt().toLocalDate().equals(today)).toList();
        int attended = (int) todaySessions.stream().map(AttendanceSession::getPersonId).distinct().count();
        int checkedIn = (int) todaySessions.stream().filter(s -> s.getCheckOutAt() == null)
                .map(AttendanceSession::getPersonId).distinct().count();
        return new AttendanceSummary(personIds.size(), attended, checkedIn);
    }

    /** 혼잡도 v1 — 시간대별 재실 인원(오늘 + 이번주 평균). */
    private Congestion congestion(List<Long> personIds, LocalDate today, LocalDateTime now) {
        if (personIds.isEmpty()) return new Congestion(new int[24], new int[24]);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        List<AttendanceSession> weekSessions = attendance
                .findByPersonIdInAndCheckInAtGreaterThanEqualOrderByCheckInAtDesc(personIds, weekStart.atStartOfDay());
        Map<LocalDate, List<AttendanceSession>> byDay = weekSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getCheckInAt().toLocalDate()));

        int[] todayByHour = presentByHour(byDay.getOrDefault(today, List.of()), today, now);

        int[] sum = new int[24];
        int daysWithData = 0;
        for (LocalDate d = weekStart; !d.isAfter(today); d = d.plusDays(1)) {
            List<AttendanceSession> ds = byDay.getOrDefault(d, List.of());
            if (ds.isEmpty()) continue;
            daysWithData++;
            int[] c = presentByHour(ds, d, now);
            for (int h = 0; h < 24; h++) sum[h] += c[h];
        }
        int denom = Math.max(1, daysWithData);
        int[] weekAvg = new int[24];
        for (int h = 0; h < 24; h++) weekAvg[h] = Math.round((float) sum[h] / denom);
        return new Congestion(todayByHour, weekAvg);
    }

    /** 특정 날짜에 시간(0~23)별로 "체크인 상태였던" 인원 수. 미퇴근은 오늘이면 현재까지, 과거일이면 자정까지. */
    private int[] presentByHour(List<AttendanceSession> sessions, LocalDate day, LocalDateTime now) {
        int[] counts = new int[24];
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        for (AttendanceSession s : sessions) {
            LocalDateTime in = s.getCheckInAt();
            LocalDateTime start = in.isBefore(dayStart) ? dayStart : in;
            LocalDateTime end;
            if (s.getCheckOutAt() != null) end = s.getCheckOutAt();
            else end = day.equals(now.toLocalDate()) ? now : dayEnd;
            if (end.isAfter(dayEnd)) end = dayEnd;
            if (!start.isBefore(end)) continue;
            int hStart = start.getHour();
            int hEnd = end.minusNanos(1).getHour(); // 마지막 포함 시각의 시
            for (int h = hStart; h <= hEnd && h <= 23; h++) counts[h]++;
        }
        return counts;
    }

    private Long requireClientOrg(AuthenticatedUser actor) {
        User u = users.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
        Long orgId = u.getClientOrgId();
        if (orgId == null) {
            throw ApiException.forbidden("NO_CLIENT_ORG", "원청이 지정되지 않은 계정입니다");
        }
        return orgId;
    }

    private Map<Long, Person> loadPersons(List<Long> a, List<Long> b) {
        List<Long> ids = new ArrayList<>(a);
        b.forEach(id -> { if (id != null && !ids.contains(id)) ids.add(id); });
        if (ids.isEmpty()) return Map.of();
        return persons.findAllById(ids).stream().collect(Collectors.toMap(Person::getId, Function.identity()));
    }

    private String personName(Map<Long, Person> map, Long personId) {
        Person p = personId == null ? null : map.get(personId);
        return p != null ? p.getName() : null;
    }

    private String ownerLabel(OwnerType type, Long ownerId, Map<Long, Equipment> eqMap, Map<Long, Person> personMap) {
        if (type == OwnerType.EQUIPMENT) {
            Equipment e = eqMap.get(ownerId);
            return e != null ? e.getVehicleNo() : ("장비 #" + ownerId);
        }
        Person p = personMap.get(ownerId);
        return p != null ? p.getName() : ("인원 #" + ownerId);
    }

    private String companyName(Long companyId) {
        if (companyId == null) return null;
        return companies.findById(companyId).map(Company::getName).orElse(null);
    }

    private static String participantTypeLabel(SiteParticipantType type) {
        if (type == SiteParticipantType.EQUIPMENT_SUPPLIER) return "장비공급사";
        if (type == SiteParticipantType.MANPOWER_SUPPLIER) return "인력공급사";
        return type != null ? type.name() : null;
    }
}
