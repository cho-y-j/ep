package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.site.Site;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.weather.KmaWeatherClient;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S1 강풍 작업중지 자동 경보(§5) — 폭염 엔진 쌍둥이. 매시 50분(초단기실황 제공 후).
 * 출근 인원 있는 현장별 KMA 풍속 조회 → 현장 설정 임계(기본 10m/s) 초과 시 긴급 경보,
 * 임계 이하 복귀 시 해제. 상태 전이(진입 1회·해제 1회)만 발송해 스팸 방지(SiteWindState).
 * 안전 증거 사슬: 알림 생성 타임스탬프 + SiteWindState 진입/해제 시각 기록.
 */
@Component
@RequiredArgsConstructor
public class WindStopScheduler {

    private static final Logger log = LoggerFactory.getLogger(WindStopScheduler.class);

    private final AttendanceSessionRepository attendanceSessions;
    private final WorkPlanRepository workPlans;
    private final SiteRepository sites;
    private final PersonRepository persons;
    private final KmaWeatherClient weather;
    private final SiteSafetySettingsRepository safetySettings;
    private final SiteWindStateRepository windStates;
    private final SiteParticipantRepository participants;
    private final FieldSafetyAlertRepository alertRepo;
    private final SafetyAlertBroadcaster broadcaster;
    private final NotificationService notifications;

    public enum Transition { ENTER, CLEAR, NONE }

    /** 순수 전이 판정(단위 테스트용) — 풍속 미상이면 판단 보류. */
    public static Transition decide(Double windMps, double threshold, boolean wasActive) {
        if (windMps == null) return Transition.NONE;
        boolean over = windMps >= threshold;
        if (over && !wasActive) return Transition.ENTER;
        if (!over && wasActive) return Transition.CLEAR;
        return Transition.NONE;
    }

    @Scheduled(cron = "0 50 * * * *")
    @Transactional
    public void checkWindAlerts() {
        List<AttendanceSession> open = attendanceSessions.findByCheckOutAtIsNull();
        if (open.isEmpty()) return;

        // 현장별 출근 세션 그룹핑 (session → workPlan → site).
        Map<Long, List<AttendanceSession>> bySite = new HashMap<>();
        Map<Long, Long> siteBp = new HashMap<>();
        Map<Long, WorkPlan> wpCache = new HashMap<>();
        for (AttendanceSession s : open) {
            if (s.getWorkPlanId() == null) continue;
            WorkPlan wp = wpCache.computeIfAbsent(s.getWorkPlanId(),
                    id -> workPlans.findById(id).orElse(null));
            if (wp == null || wp.getSiteId() == null) continue;
            bySite.computeIfAbsent(wp.getSiteId(), k -> new ArrayList<>()).add(s);
            siteBp.putIfAbsent(wp.getSiteId(), wp.getBpCompanyId());
        }

        for (Map.Entry<Long, List<AttendanceSession>> e : bySite.entrySet()) {
            Long siteId = e.getKey();
            Site site = sites.findById(siteId).orElse(null);
            if (site == null || site.getLatitude() == null || site.getLongitude() == null) continue;

            KmaWeatherClient.SiteWeather w = weather.fetch(site.getLatitude(), site.getLongitude()).orElse(null);
            Double wind = w != null ? w.windMps() : null;
            double threshold = safetySettings.findBySiteId(siteId)
                    .map(SiteSafetySettings::getWindStopMps).orElse(SafetyThresholds.LEGAL_WIND_STOP);

            SiteWindState state = windStates.findBySiteId(siteId).orElseGet(() -> SiteWindState.of(siteId));
            Transition t = decide(wind, threshold, state.isActive());
            if (t == Transition.NONE) continue;

            LocalDateTime now = LocalDateTime.now();
            if (t == Transition.ENTER) {
                state.enter(wind, now);
                windStates.save(state);
                onEnter(site, siteBp.get(siteId), e.getValue(), wind, threshold);
            } else {
                state.clear(wind, now);
                windStates.save(state);
                onClear(site, siteBp.get(siteId), wind, threshold);
            }
        }
    }

    /** 초과 진입 — 작업자별 alert 생성 + 관제 WS + 작업자 FCM(1회 배치) + BP·공급사 인앱 알림. */
    private void onEnter(Site site, Long bpCompanyId, List<AttendanceSession> attendees,
                         double wind, double threshold) {
        List<Long> personIds = attendees.stream().map(AttendanceSession::getPersonId).distinct().toList();
        Map<Long, Person> personById = new HashMap<>();
        persons.findAllById(personIds).forEach(p -> personById.put(p.getId(), p));

        String msg = String.format("강풍 작업중지 — 풍속 %.1fm/s (기준 %sm/s 초과). 옥외 작업을 즉시 중지하세요. 현장: %s",
                wind, fmt(threshold), site.getName());

        List<FieldSafetyAlert> created = new ArrayList<>();
        for (AttendanceSession s : attendees) {
            if (!personById.containsKey(s.getPersonId())) continue;
            FieldSafetyAlert a = new FieldSafetyAlert();
            a.setPersonId(s.getPersonId());
            a.setWorkPlanId(s.getWorkPlanId());
            a.setSiteId(site.getId());
            a.setBpCompanyId(bpCompanyId);
            a.setKind("wind_stop");
            a.setLevel("danger");
            a.setSeverity(SafetySeverity.EMERGENCY.name());   // S5': 강풍 작업중지 = 긴급.
            a.setMessage(msg);
            a.setLat(site.getLatitude());   // 현장 좌표 — 관제 지도 표기.
            a.setLng(site.getLongitude());
            alertRepo.save(a);
            created.add(a);
        }
        broadcaster.publishWindStop(created, personById, msg);

        // BP + 현장 참여 공급사 관리자 인앱 알림.
        for (Long companyId : notifyCompanies(site.getId(), bpCompanyId)) {
            notifications.sendToCompany(companyId, NotificationType.WIND_STOP,
                    "강풍 작업중지 경보", msg, "SITE", site.getId(), site.getId(), "시스템 (강풍 경보)");
        }
        log.warn("WindStopScheduler ENTER site={} wind={} threshold={} alerts={}",
                site.getId(), wind, threshold, created.size());
    }

    /** 해제 — 미해결 강풍 alert 처리완료 + BP·공급사 해제 통지. */
    private void onClear(Site site, Long bpCompanyId, double wind, double threshold) {
        LocalDateTime now = LocalDateTime.now();
        int resolved = 0;
        for (FieldSafetyAlert a : alertRepo.findBySiteIdOrderByCreatedAtDesc(site.getId())) {
            if ("wind_stop".equals(a.getKind()) && !a.isResolved()) {
                a.setResolved(true);
                a.setResolvedAt(now);
                broadcaster.publishResolved(a);
                resolved++;
            }
        }
        String msg = String.format("강풍 해제 — 풍속 %.1fm/s (기준 %sm/s 이하). 현장: %s",
                wind, fmt(threshold), site.getName());
        for (Long companyId : notifyCompanies(site.getId(), bpCompanyId)) {
            notifications.sendToCompany(companyId, NotificationType.WIND_CLEARED,
                    "강풍 해제", msg, "SITE", site.getId(), site.getId(), "시스템 (강풍 해제)");
        }
        log.info("WindStopScheduler CLEAR site={} wind={} resolved={}", site.getId(), wind, resolved);
    }

    /** 통지 대상 회사 — BP + 현장 ACTIVE 참여 공급사. */
    private List<Long> notifyCompanies(Long siteId, Long bpCompanyId) {
        List<Long> ids = new ArrayList<>();
        if (bpCompanyId != null) ids.add(bpCompanyId);
        participants.findBySiteIdOrderByIdDesc(siteId).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                .map(p -> p.getCompanyId())
                .filter(id -> !ids.contains(id))
                .forEach(ids::add);
        return ids;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
