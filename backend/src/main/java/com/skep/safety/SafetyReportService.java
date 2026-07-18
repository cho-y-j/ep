package com.skep.safety;

import com.skep.clientorg.ClientOrg;
import com.skep.clientorg.ClientOrgRepository;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.DailyWorkLogRepository;
import com.skep.dailywork.WorkLogSignStatus;
import com.skep.equipment.DailyEquipmentInspection;
import com.skep.equipment.DailyEquipmentInspectionRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.legalinspection.LegalInspection;
import com.skep.legalinspection.LegalInspectionRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.dto.SafetyReportDtos.*;
import com.skep.safety.dto.SiteSafetySettingsResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.signature.SignatureRole;
import com.skep.signature.SignatureStatus;
import com.skep.signature.WorksheetSignatureRepository;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * P3d 안전관리 이행 보고서 — 현장·기간별 증거사슬 집계(읽기전용, 기존 데이터 실사만).
 * 권한: ADMIN(전체) · BP(자기 현장) · CLIENT(자기 원청 현장). 공급사/작업자는 접근 불가(SecurityConfig + 여기 재검증).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SafetyReportService {

    private static final int DEFAULT_DAYS = 30;

    /** 서명 완결 판정 대상 계획서 상태(초안·취소는 제외). */
    private static final Set<WorkPlanStatus> SIGNATURE_RELEVANT = EnumSet.of(
            WorkPlanStatus.SUBMITTED, WorkPlanStatus.APPROVED, WorkPlanStatus.IN_PROGRESS, WorkPlanStatus.DONE);

    private final SiteRepository sites;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final ClientOrgRepository clientOrgs;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final FieldSafetyAlertRepository alerts;
    private final LegalInspectionRepository legalInspections;
    private final DailyEquipmentInspectionRepository operatorInspections;
    private final WorkPlanRepository workPlans;
    private final WorksheetSignatureRepository signatures;
    private final DailyWorkLogRepository dailyWorkLogs;
    private final SiteWindStateRepository windStates;
    private final SiteSafetySettingsRepository safetySettings;

    public SafetyReport report(Long siteId, LocalDate fromParam, LocalDate toParam, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        ensureCanView(site, actor);

        LocalDate to = toParam != null ? toParam : LocalDate.now();
        LocalDate from = fromParam != null ? fromParam : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_PERIOD", "시작일이 종료일보다 늦습니다");
        }
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(LocalTime.MAX);

        // ── 배치 조회(N+1 회피) ──────────────────────────────────
        List<Long> eqIds = equipments.findByCurrentSiteIdIn(List.of(siteId)).stream()
                .map(Equipment::getId).toList();
        List<FieldSafetyAlert> periodAlerts =
                alerts.findBySiteIdAndCreatedAtBetweenOrderByCreatedAtAsc(siteId, fromDt, toDt);
        List<LegalInspection> legals =
                legalInspections.findBySiteIdAndInspectDateBetweenOrderByInspectDateAsc(siteId, from, to);
        List<DailyEquipmentInspection> operators = eqIds.isEmpty() ? List.of()
                : operatorInspections.findByEquipmentIdInAndInspectDateBetween(eqIds, from, to);
        List<WorkPlan> plans = workPlans.findBySiteIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(siteId, from, to);
        List<WorkPlan> activePlans = plans.stream()
                .filter(p -> SIGNATURE_RELEVANT.contains(p.getStatus())).toList();
        Map<Long, Set<SignatureRole>> signedRoles = signedRolesByPlan(activePlans);
        List<DailyWorkLog> logs =
                dailyWorkLogs.findBySiteIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(siteId, from, to);

        // ── ① 요약 ───────────────────────────────────────────────
        AlertSummary alertSummary = SafetyReportCalculator.alertSummary(periodAlerts);
        InspectionSummary inspectionSummary = inspectionSummary(legals, operators, eqIds.size());
        WorkComplianceSummary workComplianceSummary = workComplianceSummary(activePlans, signedRoles, logs);

        // ── ② 타임라인 ───────────────────────────────────────────
        List<TimelineDay> timeline = buildTimeline(periodAlerts, legals, operators, siteId, from, to);
        Set<LocalDate> legalDays = legals.stream().map(LegalInspection::getInspectDate).collect(Collectors.toSet());
        Set<LocalDate> operatorDays = operators.stream().map(DailyEquipmentInspection::getInspectDate).collect(Collectors.toSet());

        // ── ③ 미이행 ─────────────────────────────────────────────
        Noncompliance noncompliance = buildNoncompliance(periodAlerts, activePlans, signedRoles, logs, legalDays, operatorDays);

        // ── ④ 현행 안전 기준 ─────────────────────────────────────
        SiteSafetySettingsResponse standard = safetySettings.findBySiteId(siteId)
                .map(SiteSafetySettingsResponse::of)
                .orElseGet(() -> SiteSafetySettingsResponse.ofDefaults(siteId));

        String bpName = site.getBpCompanyId() == null ? null
                : companies.findById(site.getBpCompanyId()).map(Company::getName).orElse(null);
        String clientOrgName = site.getClientOrgId() == null ? null
                : clientOrgs.findById(site.getClientOrgId()).map(ClientOrg::getName).orElse(null);

        return new SafetyReport(
                site.getId(), site.getName(), site.getCode(), bpName, clientOrgName,
                from, to, LocalDateTime.now(), actor.name(),
                alertSummary, inspectionSummary, workComplianceSummary,
                timeline, noncompliance, standard);
    }

    // ── 집계 helpers ─────────────────────────────────────────────

    private InspectionSummary inspectionSummary(List<LegalInspection> legals,
                                                List<DailyEquipmentInspection> operators, int targetEquipment) {
        int legalTotal = legals.size();
        int legalDays = (int) legals.stream().map(LegalInspection::getInspectDate).distinct().count();
        int nfcVerified = (int) legals.stream().filter(LegalInspection::isTagVerified).count();
        Integer nfcRatePct = legalTotal == 0 ? null : (int) Math.round(nfcVerified * 100.0 / legalTotal);
        int operatorTotal = operators.size();
        int operatorDays = (int) operators.stream().map(DailyEquipmentInspection::getInspectDate).distinct().count();
        return new InspectionSummary(legalTotal, legalDays, nfcVerified, nfcRatePct,
                operatorTotal, operatorDays, targetEquipment);
    }

    private WorkComplianceSummary workComplianceSummary(List<WorkPlan> activePlans,
                                                        Map<Long, Set<SignatureRole>> signedRoles, List<DailyWorkLog> logs) {
        int planTotal = activePlans.size();
        int planFullySigned = (int) activePlans.stream().filter(p -> isFullySigned(signedRoles.get(p.getId()))).count();
        int logTotal = logs.size();
        int logSigned = (int) logs.stream().filter(l -> l.getSignStatus() == WorkLogSignStatus.SIGNED
                || l.getSignStatus() == WorkLogSignStatus.PHOTO).count();
        Integer logSignRatePct = logTotal == 0 ? null : (int) Math.round(logSigned * 100.0 / logTotal);
        return new WorkComplianceSummary(planTotal, planFullySigned, logTotal, logSigned, logSignRatePct);
    }

    private List<TimelineDay> buildTimeline(List<FieldSafetyAlert> periodAlerts, List<LegalInspection> legals,
                                            List<DailyEquipmentInspection> operators, Long siteId,
                                            LocalDate from, LocalDate to) {
        Map<LocalDate, List<TimelineAlert>> alertsByDay = new TreeMap<>();
        for (FieldSafetyAlert a : periodAlerts) {
            alertsByDay.computeIfAbsent(a.getCreatedAt().toLocalDate(), d -> new ArrayList<>()).add(toTimelineAlert(a));
        }
        Map<LocalDate, Integer> legalByDay = new TreeMap<>();
        for (LegalInspection l : legals) legalByDay.merge(l.getInspectDate(), 1, Integer::sum);
        Map<LocalDate, Integer> operatorByDay = new TreeMap<>();
        for (DailyEquipmentInspection o : operators) operatorByDay.merge(o.getInspectDate(), 1, Integer::sum);

        Map<LocalDate, WindEvent> windByDay = new TreeMap<>();
        windStates.findBySiteId(siteId).ifPresent(w -> {
            WindEvent ev = new WindEvent(w.getEnteredAt(), w.getClearedAt(), w.getWindMps());
            LocalDate day = inRange(w.getEnteredAt(), from, to) ? w.getEnteredAt().toLocalDate()
                    : inRange(w.getClearedAt(), from, to) ? w.getClearedAt().toLocalDate() : null;
            if (day != null) windByDay.put(day, ev);
        });

        Set<LocalDate> allDays = new TreeSet<>();
        allDays.addAll(alertsByDay.keySet());
        allDays.addAll(legalByDay.keySet());
        allDays.addAll(operatorByDay.keySet());
        allDays.addAll(windByDay.keySet());

        List<TimelineDay> out = new ArrayList<>();
        for (LocalDate d : allDays) {
            out.add(new TimelineDay(d,
                    alertsByDay.getOrDefault(d, List.of()),
                    legalByDay.getOrDefault(d, 0),
                    operatorByDay.getOrDefault(d, 0),
                    windByDay.get(d)));
        }
        return out;
    }

    private Noncompliance buildNoncompliance(List<FieldSafetyAlert> periodAlerts, List<WorkPlan> activePlans,
                                             Map<Long, Set<SignatureRole>> signedRoles, List<DailyWorkLog> logs,
                                             Set<LocalDate> legalDays, Set<LocalDate> operatorDays) {
        List<TimelineAlert> unacked = periodAlerts.stream()
                .filter(a -> SafetyReportCalculator.subjectToAck(a) && a.getAcknowledgedAt() == null)
                .map(this::toTimelineAlert).toList();

        // 작업 예정일(활성 계획서 작업일) 중 점검 기록이 전혀 없는 날(법정·조종원 둘 다 없음).
        List<LocalDate> uninspected = activePlans.stream().map(WorkPlan::getWorkDate).distinct()
                .filter(d -> !legalDays.contains(d) && !operatorDays.contains(d))
                .sorted().toList();

        List<UnsignedPlan> unsignedPlans = activePlans.stream()
                .filter(p -> !isFullySigned(signedRoles.get(p.getId())))
                .map(p -> new UnsignedPlan(p.getId(), p.getWorkDate(), p.getTitle(),
                        pendingRoleLabels(signedRoles.get(p.getId()))))
                .toList();

        List<DailyWorkLog> unsigned = logs.stream()
                .filter(l -> l.getSignStatus() == WorkLogSignStatus.UNSIGNED).toList();
        List<UnsignedLog> unsignedLogs = toUnsignedLogs(unsigned);

        return new Noncompliance(unacked, uninspected, unsignedPlans, unsignedLogs);
    }

    private TimelineAlert toTimelineAlert(FieldSafetyAlert a) {
        return new TimelineAlert(a.getId(), a.getKind(), SafetyReportCalculator.kindLabel(a.getKind()),
                a.getSeverity(), a.getLevel(), a.getCreatedAt(), a.getAcknowledgedAt(),
                SafetyReportCalculator.ackElapsedSeconds(a), a.getEscalatedAt(), a.isResolved(),
                SafetyReportCalculator.subjectToAck(a));
    }

    private List<UnsignedLog> toUnsignedLogs(List<DailyWorkLog> unsigned) {
        if (unsigned.isEmpty()) return List.of();
        Map<Long, Equipment> eqMap = equipments.findAllById(
                unsigned.stream().map(DailyWorkLog::getEquipmentId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Equipment::getId, Function.identity()));
        Map<Long, Person> personMap = persons.findAllById(
                unsigned.stream().map(DailyWorkLog::getPersonId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        return unsigned.stream()
                .map(l -> new UnsignedLog(l.getId(), l.getWorkDate(), logLabel(l, eqMap, personMap)))
                .toList();
    }

    private String logLabel(DailyWorkLog l, Map<Long, Equipment> eqMap, Map<Long, Person> personMap) {
        if (l.getEquipmentId() != null) {
            Equipment e = eqMap.get(l.getEquipmentId());
            if (e != null) return e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "장비 #" + e.getId());
        }
        if (l.getPersonId() != null) {
            Person p = personMap.get(l.getPersonId());
            if (p != null) return p.getName();
        }
        return l.getWorkContent() != null ? l.getWorkContent() : "확인서 #" + l.getId();
    }

    /** 활성 계획서들의 SIGNED 역할 집합(PNG 미로드 projection). */
    private Map<Long, Set<SignatureRole>> signedRolesByPlan(List<WorkPlan> activePlans) {
        if (activePlans.isEmpty()) return Map.of();
        List<Long> ids = activePlans.stream().map(WorkPlan::getId).toList();
        Map<Long, Set<SignatureRole>> map = new java.util.HashMap<>();
        for (Object[] row : signatures.findStatusesByWorkPlanIds(ids)) {
            Long planId = (Long) row[0];
            SignatureRole role = (SignatureRole) row[1];
            SignatureStatus status = (SignatureStatus) row[2];
            if (status == SignatureStatus.SIGNED) {
                map.computeIfAbsent(planId, k -> EnumSet.noneOf(SignatureRole.class)).add(role);
            }
        }
        return map;
    }

    private static boolean isFullySigned(Set<SignatureRole> signed) {
        return signed != null && signed.size() == SignatureRole.values().length;
    }

    private static List<String> pendingRoleLabels(Set<SignatureRole> signed) {
        List<String> out = new ArrayList<>();
        for (SignatureRole r : SignatureRole.values()) {
            if (signed == null || !signed.contains(r)) out.add(roleLabel(r));
        }
        return out;
    }

    private static String roleLabel(SignatureRole r) {
        return switch (r) {
            case AUTHOR -> "작성자";
            case SUPERVISOR -> "담당자";
            case CONFIRMER -> "확인자";
            case REVIEWER -> "검토자";
            case APPROVER -> "승인자";
        };
    }

    private static boolean inRange(LocalDateTime t, LocalDate from, LocalDate to) {
        if (t == null) return false;
        LocalDate d = t.toLocalDate();
        return !d.isBefore(from) && !d.isAfter(to);
    }

    private void ensureCanView(Site site, AuthenticatedUser actor) {
        switch (actor.role()) {
            case ADMIN -> { /* 전체 */ }
            case BP -> {
                if (actor.companyId() == null || !actor.companyId().equals(site.getBpCompanyId())) {
                    throw ApiException.forbidden("SITE_DENIED", "본인 현장만 조회할 수 있습니다");
                }
            }
            case CLIENT -> {
                User u = users.findById(actor.id())
                        .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
                if (u.getClientOrgId() == null || !u.getClientOrgId().equals(site.getClientOrgId())) {
                    throw ApiException.forbidden("CLIENT_SITE_DENIED", "내 원청 현장이 아닙니다");
                }
            }
            default -> throw ApiException.forbidden("REPORT_DENIED", "이행 보고서 조회 권한이 없습니다");
        }
    }
}
