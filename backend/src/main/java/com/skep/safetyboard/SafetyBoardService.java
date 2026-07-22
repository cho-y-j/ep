package com.skep.safetyboard;

import com.skep.announcement.Announcement;
import com.skep.announcement.AnnouncementRecipient;
import com.skep.announcement.AnnouncementRecipientRepository;
import com.skep.announcement.AnnouncementRepository;
import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.DailyEquipmentInspection;
import com.skep.equipment.DailyEquipmentInspectionRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.health.BpCheckin;
import com.skep.health.BpCheckinRepository;
import com.skep.legalinspection.LegalInspection;
import com.skep.legalinspection.LegalInspectionRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.FieldSafetyAlert;
import com.skep.safety.FieldSafetyAlertRepository;
import com.skep.safety.FieldSensorReading;
import com.skep.safety.FieldSensorReadingRepository;
import com.skep.safety.PersonVitalBaseline;
import com.skep.safety.PersonVitalBaselineRepository;
import com.skep.safety.SiteWindState;
import com.skep.safety.SiteWindStateRepository;
import com.skep.safety.WorkerWatchState;
import com.skep.safety.WorkerWatchStateRepository;
import com.skep.safetyboard.dto.SafetyBoardDtos.*;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.weather.KmaWeatherClient;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * P4a 안전 상황판 — 흩어진 안전 조각(출근 위치·경보·기상·법정/조종원 점검·공지 확인)을
 * 현장 1건 단위로 조립. BP(자기 현장)·ADMIN(전체)·CLIENT(자기 원청 현장, 읽기).
 * 게이트 로직은 기존 서비스와 동일 기준(불가침) — 여기선 읽기 집계만.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SafetyBoardService {

    private static final List<WorkPlanStatus> ACTIVE_WP = List.of(
            WorkPlanStatus.SUBMITTED, WorkPlanStatus.APPROVED, WorkPlanStatus.IN_PROGRESS, WorkPlanStatus.DONE);
    /** S5' 확인응답 대상 kind(작업자 수신 알림) — 프론트 ackState 와 동일 규칙. */
    private static final Set<String> ACK_KINDS = Set.of("wind_stop", "heat", "rest");
    /** 낙상 경보 kind — 건강지표 뱃지·필터 '경보' 포함(일반 SOS emergency 제외, SafetyReportCalculator 와 동일). */
    private static final Set<String> FALL_KINDS = Set.of("fall", "fall_detected");

    private final SiteRepository sites;
    private final UserRepository users;
    private final WorkPlanRepository workPlans;
    private final WorkPlanPersonRepository wpPersons;
    private final AttendanceSessionRepository attendance;
    private final FieldSafetyAlertRepository alerts;
    private final EquipmentRepository equipments;
    private final LegalInspectionRepository legalInspections;
    private final DailyEquipmentInspectionRepository dailyInspections;
    private final SiteWindStateRepository windStates;
    private final WorkerWatchStateRepository watchStates;
    private final FieldSensorReadingRepository sensorReadings;
    private final PersonVitalBaselineRepository vitalBaselines;
    private final BpCheckinRepository bpCheckins;
    private final KmaWeatherClient weatherClient;
    private final PersonRepository persons;
    private final CompanyRepository companies;
    private final AnnouncementRepository announcements;
    private final AnnouncementRecipientRepository announcementRecipients;

    /** 접근 가능 현장 목록 — 역할별 스코프. */
    public List<BoardSite> listSites(AuthenticatedUser actor) {
        return accessibleSites(actor).stream().map(s -> {
            long unresolved = alerts.findBySiteIdOrderByCreatedAtDesc(s.getId()).stream()
                    .filter(a -> !a.isResolved()).count();
            boolean hasGeo = s.getLatitude() != null && s.getLongitude() != null;
            return new BoardSite(s.getId(), s.getName(), s.getCode(), hasGeo, unresolved);
        }).toList();
    }

    /** 현장 상세 보드. 접근 불가 현장은 403. */
    public SiteBoard board(Long siteId, AuthenticatedUser actor) {
        Site site = requireAccess(siteId, actor);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        // 현장 작업계획서 → 배치 인원 + 오늘 출근 세션(위치 마커).
        List<WorkPlan> activePlans = workPlans.findBySiteIdAndStatusInOrderByIdDesc(siteId, ACTIVE_WP);
        List<Long> wpIds = activePlans.stream().map(WorkPlan::getId).toList();
        List<WorkPlanPerson> deployedPersons = wpIds.isEmpty() ? List.of()
                : wpPersons.findByWorkPlanIdIn(wpIds);
        List<Long> deployedPersonIds = deployedPersons.stream()
                .map(WorkPlanPerson::getPersonId).distinct().toList();
        List<AttendanceSession> todaySessions = wpIds.isEmpty() ? List.of()
                : attendance.findByWorkPlanIdInAndCheckInAtGreaterThanEqual(wpIds, todayStart).stream()
                    .filter(s -> s.getCheckInAt().toLocalDate().equals(today)).toList();

        int attended = (int) todaySessions.stream().map(AttendanceSession::getPersonId).distinct().count();
        int checkedIn = (int) todaySessions.stream().filter(s -> s.getCheckOutAt() == null)
                .map(AttendanceSession::getPersonId).distinct().count();

        // 작업자 마커 — 좌표 있는 세션, person 당 최신 1건.
        Map<Long, AttendanceSession> latestByPerson = new java.util.LinkedHashMap<>();
        for (AttendanceSession s : todaySessions.stream()
                .sorted(Comparator.comparing(AttendanceSession::getCheckInAt).reversed()).toList()) {
            if (s.getCheckInLat() == null || s.getCheckInLng() == null) continue;
            latestByPerson.putIfAbsent(s.getPersonId(), s);
        }
        Map<Long, Person> personById = loadPersons(new ArrayList<>(latestByPerson.keySet()));
        List<WorkerMarker> workers = latestByPerson.values().stream()
                .map(s -> new WorkerMarker(s.getPersonId(), personName(personById, s.getPersonId()),
                        s.getCheckInLat(), s.getCheckInLng(),
                        s.getCheckOutAt() == null, s.getCheckInAt()))
                .toList();

        // 미해결 경보 마커.
        List<FieldSafetyAlert> unresolved = alerts.findBySiteIdOrderByCreatedAtDesc(siteId).stream()
                .filter(a -> !a.isResolved()).toList();
        Map<Long, Person> alertPersons = loadPersons(unresolved.stream()
                .map(FieldSafetyAlert::getPersonId).distinct().toList());
        List<AlertMarker> alertMarkers = unresolved.stream()
                .map(a -> new AlertMarker(a.getId(), a.getKind(), a.getLevel(), a.getSeverity(), a.getMessage(),
                        personName(alertPersons, a.getPersonId()), a.getLat(), a.getLng(),
                        a.getAcknowledgedAt(), a.getEscalatedAt(), a.getCreatedAt(), isUnacked(a)))
                .toList();
        int unackedCount = (int) alertMarkers.stream().filter(AlertMarker::unacked).count();

        // 법정점검 + 조종원 일일점검 — 배치(current_site_id) 장비 기준.
        List<Long> eqIds = equipments.findByCurrentSiteIdIsNotNullOrderByIdDesc().stream()
                .filter(e -> siteId.equals(e.getCurrentSiteId()))
                .map(Equipment::getId).toList();
        int legalDone = eqIds.isEmpty() ? 0 : (int) legalInspections
                .findByEquipmentIdInAndInspectDate(eqIds, today).stream()
                .map(LegalInspection::getEquipmentId).distinct().count();
        int operatorDone = eqIds.isEmpty() ? 0 : (int) dailyInspections
                .findByEquipmentIdInAndInspectDate(eqIds, today).stream()
                .map(DailyEquipmentInspection::getEquipmentId).distinct().count();

        // 기상 — KMA 실황(체감온도·단계·풍속) + 강풍 작업중지 상태.
        Weather weather = weather(site);

        // 공지 확인율 — 현장 스코프 공지.
        List<Announcement> siteAnnouncements = announcements.findBySiteIdOrderByCreatedAtDesc(siteId);
        Map<Long, List<AnnouncementRecipient>> recipsByAnn = siteAnnouncements.isEmpty()
                ? Map.of()
                : announcementRecipients.findByAnnouncementIdIn(
                        siteAnnouncements.stream().map(Announcement::getId).toList()).stream()
                    .collect(Collectors.groupingBy(AnnouncementRecipient::getAnnouncementId));
        List<AnnouncementSummary> annSummaries = new ArrayList<>();
        int annReadTotal = 0, annRecipTotal = 0;
        for (Announcement a : siteAnnouncements) {
            List<AnnouncementRecipient> rs = recipsByAnn.getOrDefault(a.getId(), List.of());
            int read = (int) rs.stream().filter(r -> r.getReadAt() != null).count();
            annReadTotal += read;
            annRecipTotal += rs.size();
            annSummaries.add(new AnnouncementSummary(a.getId(), a.getTitle(), a.getCreatedAt(), rs.size(), read));
        }

        // P5-W0 워커 워치 타일 — 현장 스코프 최신 상태(문제 먼저 보이게 오래된 수신순).
        // P5-W1 스파크라인: 최근 2h 심박 시계열 + 개인 대역(정상범위 밴드) 배치 조회.
        LocalDateTime nowTs = LocalDateTime.now();
        List<WorkerWatchState> watch = watchStates.findBySiteId(siteId);
        List<Long> watchPersonIds = watch.stream().map(WorkerWatchState::getPersonId).toList();
        Map<Long, List<Integer>> seriesByPerson = new java.util.HashMap<>();
        Map<Long, PersonVitalBaseline> bandByPerson = Map.of();
        if (!watchPersonIds.isEmpty()) {
            for (FieldSensorReading r : sensorReadings
                    .findByPersonIdInAndRecordedAtAfterOrderByRecordedAtAsc(watchPersonIds, nowTs.minusHours(2))) {
                if (r.getHr() != null && r.getHr() > 0) {
                    seriesByPerson.computeIfAbsent(r.getPersonId(), k -> new ArrayList<>()).add(r.getHr());
                }
            }
            bandByPerson = vitalBaselines.findAllById(watchPersonIds).stream()
                    .collect(Collectors.toMap(PersonVitalBaseline::getPersonId, Function.identity()));
        }
        Map<Long, PersonVitalBaseline> bands = bandByPerson;
        // P5-W4 1겹 — 오늘 혈압 체크인(인당 최신 1건: verdict + 실측 sys/dia). 워치 상시 측정 불가라 마커/팝오버 병기.
        Map<Long, BpCheckin> bpByPerson = new java.util.LinkedHashMap<>();
        for (BpCheckin bp : bpCheckins.findBySiteIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(
                siteId, todayStart, todayStart.plusDays(1))) {
            bpByPerson.putIfAbsent(bp.getPersonId(), bp);   // 최신순 조회 → 첫 값=최신.
        }
        // 열린 낙상 경보 인원 — 이미 조회한 unresolved 재사용(추가 쿼리 없음).
        Set<Long> fallPersonIds = unresolved.stream()
                .filter(a -> FALL_KINDS.contains(a.getKind()))
                .map(FieldSafetyAlert::getPersonId).collect(Collectors.toSet());
        // 보드 인원 = 워치 상태 보유자(문제 먼저: 오래된 수신순) ∪ 오늘 혈압 체크인 보유자 — 워치 없이 혈압만 있어도 카드/요약 노출.
        java.util.LinkedHashSet<Long> boardPersonIds = new java.util.LinkedHashSet<>();
        watch.stream()
                .sorted(Comparator.comparing(WorkerWatchState::getLastSeenAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(w -> boardPersonIds.add(w.getPersonId()));
        boardPersonIds.addAll(bpByPerson.keySet());
        Map<Long, Person> boardPersons = loadPersons(new ArrayList<>(boardPersonIds));
        Map<Long, WorkerWatchState> watchByPerson = watch.stream()
                .collect(Collectors.toMap(WorkerWatchState::getPersonId, Function.identity(), (a, b) -> a));

        // 안전 지도 — 투입 인원별 조종 장비 차량번호·역할·소속(공급사)·원청(BP).
        // work_plan_persons(역할·공급사·장비) + 장비(차량/가동상태) + work_plan(BP) 조인, 장비 매칭 행 우선.
        Map<Long, Long> bpByWp = activePlans.stream()
                .filter(wp -> wp.getBpCompanyId() != null)
                .collect(Collectors.toMap(WorkPlan::getId, WorkPlan::getBpCompanyId, (a, b) -> a));
        Map<Long, WorkPlanPerson> deployByPerson = new java.util.HashMap<>();
        for (WorkPlanPerson dp : deployedPersons) {
            deployByPerson.merge(dp.getPersonId(), dp,
                    (cur, neu) -> cur.getEquipmentId() != null ? cur : neu);
        }
        List<Long> deployEqIds = deployedPersons.stream()
                .map(WorkPlanPerson::getEquipmentId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Equipment> deployEqById = deployEqIds.isEmpty() ? Map.of()
                : equipments.findAllById(deployEqIds).stream()
                    .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        java.util.Set<Long> deployCompanyIds = new java.util.HashSet<>();
        deployedPersons.forEach(dp -> deployCompanyIds.add(dp.getSupplierCompanyId()));
        deployCompanyIds.addAll(bpByWp.values());
        deployCompanyIds.remove(null);
        Map<Long, String> companyNameById = deployCompanyIds.isEmpty() ? Map.of()
                : companies.findAllById(deployCompanyIds).stream()
                    .collect(Collectors.toMap(Company::getId, Company::getName));

        List<WatchWorker> watchWorkers = boardPersonIds.stream()
                .map(pid -> {
                    WorkerWatchState w = watchByPerson.get(pid);
                    BpCheckin bp = bpByPerson.get(pid);
                    PersonVitalBaseline pv = bands.get(pid);
                    List<Integer> series = seriesByPerson.getOrDefault(pid, List.of());
                    if (series.size() > 90) series = series.subList(series.size() - 90, series.size());  // 경량 cap.
                    WorkPlanPerson dp = deployByPerson.get(pid);
                    Equipment eq = dp != null && dp.getEquipmentId() != null ? deployEqById.get(dp.getEquipmentId()) : null;
                    Long supplierId = dp != null ? dp.getSupplierCompanyId() : null;
                    Long bpId = dp != null ? bpByWp.get(dp.getWorkPlanId()) : null;
                    return new WatchWorker(
                            pid, personName(boardPersons, pid), w != null ? w.getState() : null,
                            w != null ? w.getLastSeenAt() : null,
                            w == null || w.getLastSeenAt() == null ? null : Duration.between(w.getLastSeenAt(), nowTs).getSeconds(),
                            w != null ? w.getBattery() : null, w != null ? w.getWorn() : null, w != null ? w.getHr() : null,
                            w != null ? w.getSpo2() : null,
                            w != null ? w.getBodyTemp() : null, w != null ? w.getLat() : null, w != null ? w.getLng() : null,
                            series,
                            pv != null ? pv.getRestHrLow() : null, pv != null ? pv.getRestHrHigh() : null,
                            pv != null ? pv.getWorkHrLow() : null, pv != null ? pv.getWorkHrHigh() : null,
                            pv != null && pv.isLearned(),
                            healthRisk(boardPersons, pid),
                            fallPersonIds.contains(pid),
                            bp != null ? bp.getVerdict().name() : null,
                            bp != null ? bp.getSys() : null, bp != null ? bp.getDia() : null,
                            eq != null ? eq.getVehicleNo() : null,
                            eq != null ? eq.getAssignmentStatus().name() : null,
                            dp != null ? dp.getRole() : null,
                            supplierId != null ? companyNameById.get(supplierId) : null, supplierId,
                            bpId != null ? companyNameById.get(bpId) : null, bpId);
                })
                .toList();

        // 요약 건강 타일 — 종합 건강경보(RED/혈압 BLOCK/낙상) + 혈압주의(OK 아님). 프론트 healthCat 과 동일 기준.
        int healthAlert = (int) watchWorkers.stream()
                .filter(w -> "RED".equals(w.state()) || "BLOCK".equals(w.bpVerdict()) || w.fallAlert()).count();
        int bpCaution = (int) watchWorkers.stream()
                .filter(w -> w.bpVerdict() != null && !"OK".equals(w.bpVerdict())).count();
        Summary summary = new Summary(weather,
                deployedPersonIds.size(), attended, checkedIn,
                unackedCount,
                legalDone, eqIds.size(),
                operatorDone, eqIds.size(),
                annReadTotal, annRecipTotal,
                healthAlert, bpCaution);

        return new SiteBoard(site.getId(), site.getName(), site.getCode(), site.getAddress(),
                site.getLatitude(), site.getLongitude(), site.getPolygonGeojson(),
                site.getMapZoom(), site.getGeofenceRadiusM(),
                workers, alertMarkers, summary, annSummaries, watchWorkers);
    }

    /** 공지 수신자 확인 상태(미확인자 명단). 발송 공지의 현장 접근권을 재검증. */
    public List<RecipientStatus> recipients(Long announcementId, AuthenticatedUser actor) {
        Announcement a = announcements.findById(announcementId)
                .orElseThrow(() -> ApiException.notFound("ANNOUNCEMENT_NOT_FOUND", "공지를 찾을 수 없습니다"));
        // 현장 지정 공지면 현장 접근권으로, 미지정이면 발송자(BP)·ADMIN 만.
        if (a.getSiteId() != null) {
            requireAccess(a.getSiteId(), actor);
        } else if (actor.role() == Role.BP) {
            if (a.getSenderCompanyId() == null || !a.getSenderCompanyId().equals(actor.companyId())) {
                throw ApiException.forbidden("ANNOUNCEMENT_DENIED", "본인이 발송한 공지만 조회할 수 있습니다");
            }
        } else if (actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("ANNOUNCEMENT_DENIED", "접근 권한이 없습니다");
        }
        List<AnnouncementRecipient> rs = announcementRecipients.findByAnnouncementIdOrderByIdAsc(announcementId);
        Map<Long, Person> byId = loadPersons(rs.stream().map(AnnouncementRecipient::getPersonId).toList());
        return rs.stream()
                .map(r -> new RecipientStatus(r.getPersonId(), personName(byId, r.getPersonId()), r.getReadAt()))
                .toList();
    }

    // ── 내부 ──────────────────────────────────────────────

    private List<Site> accessibleSites(AuthenticatedUser actor) {
        return switch (actor.role()) {
            case ADMIN -> sites.findAll();
            case BP -> actor.companyId() == null ? List.of()
                    : sites.findByBpCompanyIdOrderByIdDesc(actor.companyId());
            case CLIENT -> sites.findByClientOrgIdOrderByIdDesc(requireClientOrg(actor));
            default -> throw ApiException.forbidden("BOARD_DENIED", "안전 상황판 접근 권한이 없습니다");
        };
    }

    private Site requireAccess(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        switch (actor.role()) {
            case ADMIN -> { /* 전체 허용 */ }
            case BP -> {
                if (actor.companyId() == null || !actor.companyId().equals(site.getBpCompanyId())) {
                    throw ApiException.forbidden("SITE_DENIED", "본인 현장만 조회할 수 있습니다");
                }
            }
            case CLIENT -> {
                if (!requireClientOrg(actor).equals(site.getClientOrgId())) {
                    throw ApiException.forbidden("CLIENT_SITE_DENIED", "내 원청 현장이 아닙니다");
                }
            }
            default -> throw ApiException.forbidden("BOARD_DENIED", "안전 상황판 접근 권한이 없습니다");
        }
        return site;
    }

    private Weather weather(Site site) {
        boolean windStop = windStates.findBySiteId(site.getId()).map(SiteWindState::isActive).orElse(false);
        if (site.getLatitude() == null || site.getLongitude() == null) {
            return new Weather(false, null, null, null, null, null, windStop);
        }
        return weatherClient.fetch(site.getLatitude(), site.getLongitude())
                .map(w -> new Weather(true,
                        Math.round(w.feelsLike() * 10) / 10.0,
                        w.stage().name(), w.stage().label(), w.stage().level(),
                        w.windMps(), windStop))
                .orElse(new Weather(false, null, null, null, null, null, windStop));
    }

    private boolean isUnacked(FieldSafetyAlert a) {
        boolean ackable = ACK_KINDS.contains(a.getKind())
                && a.getSeverity() != null && !"NORMAL".equals(a.getSeverity());
        return ackable && a.getAcknowledgedAt() == null;
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

    private Map<Long, Person> loadPersons(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return persons.findAllById(ids).stream().collect(Collectors.toMap(Person::getId, Function.identity()));
    }

    private String personName(Map<Long, Person> map, Long personId) {
        Person p = personId == null ? null : map.get(personId);
        return p != null ? p.getName() : (personId != null ? "작업자 #" + personId : null);
    }

    /** P5-W4 2겹 — 워치 타일 고위험군 뱃지용. 미상은 NORMAL. */
    private String healthRisk(Map<Long, Person> map, Long personId) {
        Person p = personId == null ? null : map.get(personId);
        return p != null ? p.getHealthRiskLevel().name() : "NORMAL";
    }
}
