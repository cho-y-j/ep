package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.weather.HeatStage;
import com.skep.weather.KmaWeatherClient;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 폭염/근로기준법 휴식 알림 스케줄러.
 *
 * 매시 45분(초단기실황 제공 후)에 출근 중인 작업자를 순회하며:
 *  - 현장 좌표로 현재 체감온도를 조회해 폭염 단계 판정
 *  - 단계별 법정 휴식 간격(33℃ 2시간마다 / 35·38℃ 매시간 / 비폭염 4시간)을 넘긴 작업자에게
 *    FieldSafetyAlert 를 생성 → 폰 폴링을 통해 워치로 전달, 대시보드는 WebSocket 전파.
 */
@Component
@RequiredArgsConstructor
public class HeatRestScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeatRestScheduler.class);

    private final AttendanceSessionRepository attendanceSessions;
    private final WorkPlanRepository workPlans;
    private final SiteRepository sites;
    private final PersonRepository persons;
    private final FieldSafetyAlertRepository alertRepo;
    private final KmaWeatherClient weather;
    private final SafetyAlertBroadcaster broadcaster;

    @Scheduled(cron = "0 45 * * * *")
    @Transactional
    public void checkRestAlerts() {
        List<AttendanceSession> open = attendanceSessions.findByCheckOutAtIsNull();
        if (open.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        boolean midday = now.getHour() >= 14 && now.getHour() < 17;
        Map<Long, KmaWeatherClient.SiteWeather> weatherCache = new HashMap<>(); // siteId → weather(or null)
        int sent = 0;

        for (AttendanceSession s : open) {
            if (s.getBreakStartAt() != null) continue; // 휴식 중 → 알림 보류

            WorkPlan wp = workPlans.findById(s.getWorkPlanId()).orElse(null);
            if (wp == null) continue;

            KmaWeatherClient.SiteWeather w = resolveWeather(wp.getSiteId(), weatherCache);
            HeatStage stage = w != null ? w.stage() : HeatStage.NONE;

            // 휴식 타이머 기준: 마지막 휴식알림 > 지정 작업시작 > 출근시각. (작업시간 미지정 시 출근시각 fallback)
            LocalDateTime ref = s.getLastRestAlertAt() != null ? s.getLastRestAlertAt()
                    : (s.getWorkStartAt() != null ? s.getWorkStartAt() : s.getCheckInAt());
            if (Duration.between(ref, now).toMinutes() < stage.intervalMinutes()) continue;

            Person p = persons.findById(s.getPersonId()).orElse(null);
            if (p == null) continue;

            FieldSafetyAlert a = new FieldSafetyAlert();
            a.setPersonId(p.getId());
            a.setWorkPlanId(wp.getId());
            a.setSiteId(wp.getSiteId());
            a.setBpCompanyId(wp.getBpCompanyId());
            a.setKind(stage == HeatStage.NONE ? "rest" : "heat");
            a.setLevel(stage.level());
            a.setMessage(stage.restMessage(w != null ? w.feelsLike() : null, midday));
            alertRepo.save(a);
            broadcaster.publishCreated(a, p);
            s.markRestAlerted(now);
            sent++;
        }
        if (sent > 0) log.info("HeatRestScheduler: {} rest alerts sent ({} open sessions)", sent, open.size());
    }

    /** 현장별 체감온도 1회만 조회해 캐시. 좌표 없거나 조회 실패 시 null (→ 비폭염 4시간 규칙 적용). */
    private KmaWeatherClient.SiteWeather resolveWeather(Long siteId, Map<Long, KmaWeatherClient.SiteWeather> cache) {
        if (siteId == null) return null;
        if (cache.containsKey(siteId)) return cache.get(siteId);
        Site site = sites.findById(siteId).orElse(null);
        KmaWeatherClient.SiteWeather w = null;
        if (site != null && site.getLatitude() != null && site.getLongitude() != null) {
            w = weather.fetch(site.getLatitude(), site.getLongitude()).orElse(null);
        }
        cache.put(siteId, w);
        return w;
    }
}
