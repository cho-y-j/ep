package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.common.ApiException;
import com.skep.field.FieldFcmService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.weather.KmaWeatherClient;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADMIN/BP 가 JWT 인증으로 안전알림을 조회. /api/safety-alerts/* */
@RestController
@RequestMapping("/api/safety-alerts")
@RequiredArgsConstructor
public class SafetyAlertController {

    private final FieldSafetyAlertRepository alertRepo;
    private final FieldSensorReadingRepository sensorRepo;
    private final FieldBaselineRepository baselineRepo;
    private final PersonRepository persons;
    private final AttendanceSessionRepository attendanceSessions;
    private final WorkPlanRepository workPlans;
    private final SiteRepository sites;
    private final KmaWeatherClient weatherClient;
    private final FieldFcmService fcm;

    /** 전체(또는 unresolved 만). ADMIN 전체 / BP 자기 회사만. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public List<Map<String, Object>> list(@CurrentUser AuthenticatedUser actor,
                                          @RequestParam(defaultValue = "false") boolean unresolvedOnly) {
        List<FieldSafetyAlert> rows;
        if (actor.role() == Role.ADMIN) {
            rows = unresolvedOnly
                    ? alertRepo.findByResolvedFalseOrderByCreatedAtDesc()
                    : alertRepo.findTop100ByOrderByCreatedAtDesc();
        } else {
            Long bpId = actor.companyId();
            if (bpId == null) return List.of();
            rows = alertRepo.findByBpCompanyIdOrderByCreatedAtDesc(bpId);
            if (unresolvedOnly) rows = rows.stream().filter(a -> !a.isResolved()).toList();
        }
        return toAlertMaps(rows.stream().limit(100).toList());
    }

    /** site 단위 조회. */
    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public List<Map<String, Object>> bySite(@org.springframework.web.bind.annotation.PathVariable Long siteId,
                                            @CurrentUser AuthenticatedUser actor) {
        var list = alertRepo.findBySiteIdOrderByCreatedAtDesc(siteId);
        if (actor.role() == Role.BP && actor.companyId() != null) {
            list = list.stream().filter(a -> actor.companyId().equals(a.getBpCompanyId())).toList();
        }
        return toAlertMaps(list.stream().limit(100).toList());
    }

    /** 알림 해결 처리 (ADMIN/BP). */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public Map<String, Object> resolve(@org.springframework.web.bind.annotation.PathVariable Long id,
                                       @CurrentUser AuthenticatedUser actor) {
        FieldSafetyAlert a = alertRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "알림을 찾을 수 없습니다"));
        if (actor.role() == Role.BP && actor.companyId() != null
                && !actor.companyId().equals(a.getBpCompanyId())) {
            throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        }
        a.setResolved(true);
        a.setResolvedAt(LocalDateTime.now());
        a.setResolvedByUserId(actor.id());
        alertRepo.save(a);
        return alertMap(a);
    }

    /** 같은 현장(또는 같은 작업계획서) 다른 작업자에게 "도움 요청" 푸시. ADMIN/BP. */
    @PostMapping("/{id}/dispatch-help")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public Map<String, Object> dispatchHelp(@org.springframework.web.bind.annotation.PathVariable Long id,
                                            @CurrentUser AuthenticatedUser actor) {
        FieldSafetyAlert a = alertRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "알림을 찾을 수 없습니다"));
        if (actor.role() == Role.BP && actor.companyId() != null
                && !actor.companyId().equals(a.getBpCompanyId())) {
            throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
        }

        Person victim = persons.findById(a.getPersonId()).orElse(null);
        String victimName = victim != null ? victim.getName() : ("작업자 #" + a.getPersonId());

        // 도움 대상 = 같은 site_id 의 다른 person 들 (오늘 출근한 사람 우선, 없으면 같은 work_plan 배정자).
        java.util.Set<Long> targetIds = new java.util.HashSet<>();
        if (a.getSiteId() != null) {
            // 오늘 출근 기록 있는 작업자 중 같은 site_id 만. (전체 person 테이블 로드 회피)
            var todayStart = java.time.LocalDate.now().atStartOfDay();
            for (AttendanceSession s : attendanceSessions.findByCheckInAtGreaterThanEqual(todayStart)) {
                WorkPlan wp = s.getWorkPlanId() != null ? workPlans.findById(s.getWorkPlanId()).orElse(null) : null;
                if (wp != null && Objects.equals(wp.getSiteId(), a.getSiteId())) {
                    targetIds.add(s.getPersonId());
                }
            }
        }
        targetIds.remove(a.getPersonId()); // 발신자 본인 제외

        List<Person> targets = persons.findAllById(targetIds);
        List<String> phoneTokens = targets.stream()
                .map(Person::getFcmToken).filter(Objects::nonNull).filter(t -> !t.isBlank()).toList();
        List<String> watchTokens = targets.stream()
                .map(Person::getWatchFcmToken).filter(Objects::nonNull).filter(t -> !t.isBlank()).toList();

        String title = victimName + " 긴급";
        String body = victimName + " 작업자에게 긴급상황이 발생했습니다. 가까운 분 도와주세요.";
        int phoneSent = phoneTokens.isEmpty() ? 0 : fcm.sendAnnouncement(phoneTokens, title, body);
        int watchSent = watchTokens.isEmpty() ? 0 : fcm.sendAnnouncement(watchTokens, title, body);

        Map<String, Object> out = new HashMap<>();
        out.put("ok", true);
        out.put("targets", targets.size());
        out.put("phone_sent", phoneSent);
        out.put("watch_sent", watchSent);
        return out;
    }

    /** 인원별 현재 바이탈 + 베이스라인 비교 (그래프용). BP 는 자기 회사 알림이 있는 작업자만. */
    @GetMapping("/person/{personId}/vitals")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public Map<String, Object> personVitals(@org.springframework.web.bind.annotation.PathVariable Long personId,
                                            @CurrentUser AuthenticatedUser actor) {
        if (actor.role() == Role.BP) {
            Long bpId = actor.companyId();
            boolean owns = bpId != null && alertRepo.findByPersonIdOrderByCreatedAtDesc(personId).stream()
                    .anyMatch(a -> bpId.equals(a.getBpCompanyId()));
            if (!owns) {
                throw ApiException.forbidden("FORBIDDEN", "권한이 없습니다");
            }
        }
        Person p = persons.findById(personId)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원을 찾을 수 없습니다"));
        var readings = sensorRepo.findTop50ByPersonIdOrderByRecordedAtDesc(personId);
        var baseline = baselineRepo.findById(personId).orElse(null);
        Map<String, Object> out = new HashMap<>();
        out.put("person_id", p.getId());
        out.put("person_name", p.getName());
        out.put("readings", readings.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("recorded_at", r.getRecordedAt());
            m.put("hr", r.getHr());
            m.put("spo2", r.getSpo2());
            m.put("body_temp", r.getBodyTemp());
            m.put("state", r.getState());
            return m;
        }).toList());
        if (baseline != null) {
            out.put("baseline", Map.of(
                    "hr_rest_mean", baseline.getHrRestMean(),
                    "hr_active_mean", baseline.getHrActiveMean(),
                    "spo2_mean", baseline.getSpo2Mean(),
                    "samples_count", baseline.getSamplesCount()
            ));
        }
        return out;
    }

    /** 좌표 1곳의 현재 기온/체감온도/폭염단계. 검증·임의 조회용. */
    @GetMapping("/weather")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public Map<String, Object> weather(@RequestParam double lat, @RequestParam double lng) {
        return weatherMap(weatherClient.fetch(lat, lng).orElse(null));
    }

    /** 출근 중인 작업자가 있는 현장별 현재 체감온도/폭염단계 (안전관리탭). BP 는 자기 회사 현장만. */
    @GetMapping("/site-weather")
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public List<Map<String, Object>> siteWeather(@CurrentUser AuthenticatedUser actor) {
        Long bpScope = actor.role() == Role.BP ? actor.companyId() : null;
        Map<Long, Integer> workerCount = new LinkedHashMap<>(); // siteId → 출근 인원수
        for (AttendanceSession s : attendanceSessions.findByCheckOutAtIsNull()) {
            WorkPlan wp = workPlans.findById(s.getWorkPlanId()).orElse(null);
            if (wp == null || wp.getSiteId() == null) continue;
            if (bpScope != null && !bpScope.equals(wp.getBpCompanyId())) continue;
            workerCount.merge(wp.getSiteId(), 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        // 같은 좌표는 1회만 외부 호출 — 여러 현장이 동일 좌표면 fetch 중복 제거.
        Map<String, java.util.Optional<KmaWeatherClient.SiteWeather>> weatherCache = new HashMap<>();
        for (Map.Entry<Long, Integer> e : workerCount.entrySet()) {
            Site site = sites.findById(e.getKey()).orElse(null);
            if (site == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("site_id", site.getId());
            m.put("site_name", site.getName());
            m.put("worker_count", e.getValue());
            if (site.getLatitude() != null && site.getLongitude() != null) {
                var w = weatherCache.computeIfAbsent(
                        site.getLatitude() + "," + site.getLongitude(),
                        k -> weatherClient.fetch(site.getLatitude(), site.getLongitude())).orElse(null);
                m.putAll(weatherMap(w));
            } else {
                m.put("available", false);
            }
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> weatherMap(KmaWeatherClient.SiteWeather w) {
        Map<String, Object> m = new HashMap<>();
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

    /** 목록 직렬화 — personId 를 모아 findAllById 로 배치 조회 (행별 findById N+1 제거). */
    private List<Map<String, Object>> toAlertMaps(List<FieldSafetyAlert> rows) {
        List<Long> personIds = rows.stream().map(FieldSafetyAlert::getPersonId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Person> personById = new HashMap<>();
        for (Person p : persons.findAllById(personIds)) personById.put(p.getId(), p);
        return rows.stream().map(a -> alertMap(a, personById.get(a.getPersonId()))).toList();
    }

    private Map<String, Object> alertMap(FieldSafetyAlert a) {
        return alertMap(a, persons.findById(a.getPersonId()).orElse(null));
    }

    private Map<String, Object> alertMap(FieldSafetyAlert a, Person p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("person_id", a.getPersonId());
        m.put("person_name", p != null ? p.getName() : null);
        m.put("person_phone", p != null ? p.getPhone() : null);
        m.put("person_has_photo", p != null && p.getPhotoKey() != null);
        m.put("kind", a.getKind());
        m.put("level", a.getLevel());
        m.put("message", a.getMessage());
        m.put("hr", a.getHr());
        m.put("spo2", a.getSpo2());
        m.put("body_temp", a.getBodyTemp());
        m.put("lat", a.getLat());
        m.put("lng", a.getLng());
        m.put("site_id", a.getSiteId());
        m.put("work_plan_id", a.getWorkPlanId());
        m.put("resolved", a.isResolved());
        m.put("resolved_at", a.getResolvedAt());
        m.put("created_at", a.getCreatedAt());
        return m;
    }
}
