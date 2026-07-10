package com.skep.field;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.FieldSafetyAlert;
import com.skep.safety.FieldSafetyAlertRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.site.SiteService;
import com.skep.user.Role;
import com.skep.weather.KmaWeatherClient;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanEquipment;
import com.skep.workplan.WorkPlanEquipmentRepository;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BP·공급사·ADMIN 공용 "현장 상황" 통합 조회. 6요소(현장/출근/안전/날씨/작업진행/투입자원)를 한 번에 조립.
 * - 인가: SiteService.get 이 감싼 ensureCanView 로 행 단위 가시성(403/404) 검증.
 * - 공급사 스코프: 출근·안전·투입자원은 자사(+직속 자식)만. 공통(현장/날씨/작업진행)은 전원 동일.
 * 신규 조회만 — 쓰기/마이그레이션 없음.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteSituationService {

    private static final int ALERT_LIMIT = 20;

    private final SiteService siteService;
    private final SiteRepository sites;
    private final CompanyService companyService;
    private final CompanyRepository companies;
    private final PersonRepository persons;
    private final EquipmentRepository equipments;
    private final AttendanceSessionRepository attendanceSessions;
    private final FieldSafetyAlertRepository alertRepo;
    private final KmaWeatherClient weatherClient;
    private final WorkPlanRepository workPlans;
    private final WorkPlanEquipmentRepository wpeRepo;
    private final WorkPlanPersonRepository wppRepo;

    public Map<String, Object> situation(Long siteId, AuthenticatedUser actor) {
        // 인가: 현장 접근 권한 검증(없으면 403/404). 없는 현장이면 여기서 notFound.
        siteService.get(siteId, actor);
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));

        // 공급사면 자사(+직속 자식)로 스코프. ADMIN/BP 는 null(현장 전체).
        List<Long> supplierScope = isSupplier(actor)
                ? companyService.selfAndChildren(actor.companyId())
                : null;
        Set<Long> scope = supplierScope != null ? new HashSet<>(supplierScope) : null;

        LocalDate today = LocalDate.now();
        List<WorkPlan> todayPlans = workPlans.findBySiteIdAndWorkDateOrderByStartTimeAsc(siteId, today);

        // 이 현장 미퇴근 출근 세션 (site-wide) — today_attended 판정에도 재사용.
        List<AttendanceSession> siteOpenSessions = openSessionsForSite(siteId);
        Set<Long> checkedInPersonIds = siteOpenSessions.stream()
                .map(AttendanceSession::getPersonId).collect(Collectors.toSet());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("site", siteSection(site));
        out.put("attendance", attendanceSection(siteOpenSessions, scope));
        out.put("safety", safetySection(siteId, scope));
        out.put("weather", weatherSection(site));
        out.put("work_progress", workProgressSection(todayPlans));
        out.put("resources", resourcesSection(todayPlans, checkedInPersonIds, scope));
        return out;
    }

    // ── 1. 현장(공통) ─────────────────────────────────────────────
    private Map<String, Object> siteSection(Site site) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("site_id", site.getId());
        m.put("name", site.getName());
        m.put("address", site.getAddress());
        m.put("detail_address", site.getDetailAddress());
        m.put("latitude", site.getLatitude());
        m.put("longitude", site.getLongitude());
        m.put("geofence_radius_m", site.getGeofenceRadiusM());
        if (site.getLatitude() != null && site.getLongitude() != null) {
            int radius = site.getGeofenceRadiusM() != null ? site.getGeofenceRadiusM() : 100;
            m.put("map_url", "/api/field-auth/map?lat=" + site.getLatitude()
                    + "&lng=" + site.getLongitude() + "&radius=" + radius);
        } else {
            m.put("map_url", null);
        }
        return m;
    }

    // ── 2. 출근(공급사 스코프) ─────────────────────────────────────
    private Map<String, Object> attendanceSection(List<AttendanceSession> siteOpenSessions, Set<Long> scope) {
        Map<Long, Person> personMap = personMap(siteOpenSessions.stream().map(AttendanceSession::getPersonId).toList());
        Map<Long, String> supplierNames = companyNames(personMap.values().stream().map(Person::getSupplierId).toList());

        List<Map<String, Object>> workers = new ArrayList<>();
        for (AttendanceSession s : siteOpenSessions) {
            Person p = personMap.get(s.getPersonId());
            if (p == null) continue;
            if (scope != null && !scope.contains(p.getSupplierId())) continue;
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("person_id", p.getId());
            w.put("name", p.getName());
            w.put("supplier_name", supplierNames.get(p.getSupplierId()));
            w.put("check_in_at", s.getCheckInAt());
            w.put("on_break", s.getBreakStartAt() != null);
            workers.add(w);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checked_in_count", workers.size());
        m.put("workers", workers);
        return m;
    }

    // ── 3. 안전(공급사 스코프) ─────────────────────────────────────
    private Map<String, Object> safetySection(Long siteId, Set<Long> scope) {
        List<FieldSafetyAlert> alerts = alertRepo.findBySiteIdOrderByCreatedAtDesc(siteId);
        Map<Long, Person> personMap = personMap(alerts.stream().map(FieldSafetyAlert::getPersonId).toList());
        if (scope != null) {
            alerts = alerts.stream()
                    .filter(a -> {
                        Person p = personMap.get(a.getPersonId());
                        return p != null && scope.contains(p.getSupplierId());
                    })
                    .toList();
        }
        long unresolved = alerts.stream().filter(a -> !a.isResolved()).count();
        List<Map<String, Object>> rows = alerts.stream().limit(ALERT_LIMIT).map(a -> {
            Person p = personMap.get(a.getPersonId());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", a.getId());
            r.put("person_name", p != null ? p.getName() : null);
            r.put("kind", a.getKind());
            r.put("level", a.getLevel());
            r.put("resolved", a.isResolved());
            r.put("created_at", a.getCreatedAt());
            r.put("lat", a.getLat());
            r.put("lng", a.getLng());
            return r;
        }).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unresolved_count", unresolved);
        m.put("alerts", rows);
        return m;
    }

    // ── 4. 날씨(공통) ─────────────────────────────────────────────
    private Map<String, Object> weatherSection(Site site) {
        if (site.getLatitude() == null || site.getLongitude() == null) {
            return Map.of("available", false);
        }
        return weatherMap(weatherClient.fetch(site.getLatitude(), site.getLongitude()).orElse(null));
    }

    private Map<String, Object> weatherMap(KmaWeatherClient.SiteWeather w) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (w == null) {
            m.put("available", false);
            return m;
        }
        m.put("available", true);
        m.put("temp_c", Math.round(w.tempC() * 10) / 10.0);
        m.put("humidity", w.humidity());
        m.put("feels_like", Math.round(w.feelsLike() * 10) / 10.0);
        m.put("stage", w.stage().name());
        m.put("stage_label", w.stage().label());
        m.put("level", w.stage().level());
        return m;
    }

    // ── 5. 작업 진행(공통) ─────────────────────────────────────────
    private Map<String, Object> workProgressSection(List<WorkPlan> todayPlans) {
        List<Map<String, Object>> plans = todayPlans.stream().map(wp -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("work_plan_id", wp.getId());
            r.put("title", wp.getTitle());
            r.put("status", wp.getStatus());
            r.put("start_time", wp.getStartTime());
            r.put("end_time", wp.getEndTime());
            return r;
        }).toList();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today_total", plans.size());
        m.put("plans", plans);
        return m;
    }

    // ── 6. 투입 자원(공급사 스코프) ────────────────────────────────
    private Map<String, Object> resourcesSection(List<WorkPlan> todayPlans, Set<Long> checkedInPersonIds, Set<Long> scope) {
        List<Long> wpIds = todayPlans.stream().map(WorkPlan::getId).toList();
        if (wpIds.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("equipment_count", 0);
            empty.put("person_count", 0);
            empty.put("items", List.of());
            return empty;
        }

        List<WorkPlanEquipment> wpes = wpeRepo.findByWorkPlanIdIn(wpIds);
        List<WorkPlanPerson> wpps = wppRepo.findByWorkPlanIdIn(wpIds);

        // 장비 가동 판정용 운전자 매핑(site-wide, 스코프 필터 전).
        Map<Long, Set<Long>> driversByEquipment = new HashMap<>();
        for (WorkPlanPerson wpp : wpps) {
            if (wpp.getEquipmentId() != null) {
                driversByEquipment.computeIfAbsent(wpp.getEquipmentId(), k -> new HashSet<>()).add(wpp.getPersonId());
            }
        }

        Map<Long, Equipment> equipmentMap = equipments.findAllById(
                        wpes.stream().map(WorkPlanEquipment::getEquipmentId).distinct().toList()).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        Map<Long, Person> personMap = personMap(wpps.stream().map(WorkPlanPerson::getPersonId).toList());
        List<Long> supplierIds = new ArrayList<>();
        wpes.forEach(e -> supplierIds.add(e.getSupplierCompanyId()));
        wpps.forEach(p -> supplierIds.add(p.getSupplierCompanyId()));
        Map<Long, String> supplierNames = companyNames(supplierIds);

        List<Map<String, Object>> items = new ArrayList<>();
        int equipmentCount = 0;
        int personCount = 0;
        Set<String> seen = new HashSet<>();

        for (WorkPlanEquipment wpe : wpes) {
            if (scope != null && !scope.contains(wpe.getSupplierCompanyId())) continue;
            String key = "EQUIPMENT:" + wpe.getEquipmentId();
            if (!seen.add(key)) continue;
            Equipment e = equipmentMap.get(wpe.getEquipmentId());
            boolean attended = driversByEquipment.getOrDefault(wpe.getEquipmentId(), Set.of())
                    .stream().anyMatch(checkedInPersonIds::contains);
            items.add(resourceItem("EQUIPMENT", wpe.getEquipmentId(),
                    e != null ? equipmentLabel(e) : ("#" + wpe.getEquipmentId()),
                    supplierNames.get(wpe.getSupplierCompanyId()), attended));
            equipmentCount++;
        }
        for (WorkPlanPerson wpp : wpps) {
            if (scope != null && !scope.contains(wpp.getSupplierCompanyId())) continue;
            String key = "PERSON:" + wpp.getPersonId();
            if (!seen.add(key)) continue;
            Person p = personMap.get(wpp.getPersonId());
            boolean attended = checkedInPersonIds.contains(wpp.getPersonId());
            items.add(resourceItem("PERSON", wpp.getPersonId(),
                    p != null ? p.getName() : ("#" + wpp.getPersonId()),
                    supplierNames.get(wpp.getSupplierCompanyId()), attended));
            personCount++;
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("equipment_count", equipmentCount);
        m.put("person_count", personCount);
        m.put("items", items);
        return m;
    }

    private Map<String, Object> resourceItem(String type, Long id, String label, String supplierName, boolean attended) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("resource_type", type);
        r.put("resource_id", id);
        r.put("label", label);
        r.put("supplier_company_name", supplierName);
        r.put("today_attended", attended);
        return r;
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────
    private boolean isSupplier(AuthenticatedUser actor) {
        return actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
    }

    /** 이 현장(siteId)에 속한 미퇴근 출근 세션. 세션→작업계획서→siteId 조인. */
    private List<AttendanceSession> openSessionsForSite(Long siteId) {
        List<AttendanceSession> open = attendanceSessions.findByCheckOutAtIsNull();
        if (open.isEmpty()) return List.of();
        Map<Long, WorkPlan> wpMap = workPlans.findAllById(
                        open.stream().map(AttendanceSession::getWorkPlanId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(WorkPlan::getId, Function.identity()));
        return open.stream()
                .filter(s -> {
                    WorkPlan wp = wpMap.get(s.getWorkPlanId());
                    return wp != null && siteId.equals(wp.getSiteId());
                })
                .toList();
    }

    private Map<Long, Person> personMap(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return persons.findAllById(distinct).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
    }

    private Map<Long, String> companyNames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return companies.findAllById(distinct).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
    }

    private String equipmentLabel(Equipment e) {
        if (e.getVehicleNo() != null && !e.getVehicleNo().isBlank()) return e.getVehicleNo();
        return e.getModel() != null ? e.getModel() : ("#" + e.getId());
    }
}
