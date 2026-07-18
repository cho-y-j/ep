package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.field.FieldFcmService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P5-W0 워치 데드맨 감시(§5.3 "무수신 자체를 신호로") — 매분 실행.
 * 활성 투입(출근 세션 열림) 작업자 중 30분+ 무수신(또는 미보고)이면 watch_offline 경보 1회 생성 +
 * 본인 폰 확인 요청 FCM(CAUTION). 이후 5분 미확인 시 기존 SafetyAckEscalationScheduler 가 관리자 통보.
 * 수신 재개 시 자동 resolve 는 /sensor upsert 가 deadman_alert_id 로 처리(재발명 금지).
 * worn=false(벗음)는 사유 라벨만 달리(오경보 방지). 출근 30분 이내는 판단 보류(막 켠 워치 첫 배치 대기).
 */
@Component
@RequiredArgsConstructor
public class WatchDeadmanScheduler {

    private static final Logger log = LoggerFactory.getLogger(WatchDeadmanScheduler.class);

    /** 무수신 판정 기준(분). */
    public static final long SILENT_AFTER_MIN = 30;

    private final AttendanceSessionRepository attendance;
    private final WorkPlanRepository workPlans;
    private final WorkerWatchStateRepository watchStates;
    private final FieldSafetyAlertRepository alertRepo;
    private final PersonRepository persons;
    private final FieldFcmService fcm;
    private final SafetyAlertBroadcaster broadcaster;

    /** 순수 판정(단위 테스트용) — 마지막 수신 시각이 없거나 기준 시간보다 오래됐으면 무수신(두절). */
    public static boolean isSilent(LocalDateTime lastSeenAt, LocalDateTime now, long afterMin) {
        return lastSeenAt == null || lastSeenAt.isBefore(now.minusMinutes(afterMin));
    }

    @Scheduled(cron = "0 * * * * *")   // 매분.
    @Transactional
    public void checkDeadman() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(SILENT_AFTER_MIN);
        List<AttendanceSession> open = attendance.findByCheckOutAtIsNull();
        if (open.isEmpty()) return;

        // 사람당 최신 open 세션 1건.
        Map<Long, AttendanceSession> byPerson = new LinkedHashMap<>();
        for (AttendanceSession s : open) {
            if (s.getCheckInAt() == null || s.getWorkPlanId() == null) continue;
            byPerson.merge(s.getPersonId(), s, (a, b) -> a.getCheckInAt().isAfter(b.getCheckInAt()) ? a : b);
        }

        int created = 0;
        for (AttendanceSession s : byPerson.values()) {
            if (s.getCheckInAt().isAfter(cutoff)) continue;   // 출근 30분 이내 → 판단 보류.
            Long personId = s.getPersonId();
            WorkerWatchState ws = watchStates.findById(personId).orElse(null);
            if (ws != null && ws.getDeadmanAlertId() != null) continue;   // 이미 열린 데드맨 경보.
            if (!isSilent(ws != null ? ws.getLastSeenAt() : null, now, SILENT_AFTER_MIN)) continue;

            WorkPlan wp = workPlans.findById(s.getWorkPlanId()).orElse(null);
            boolean removed = ws != null && Boolean.FALSE.equals(ws.getWorn());   // 벗음 사유 라벨.
            fireDeadman(personId, s.getWorkPlanId(),
                    wp != null ? wp.getSiteId() : null, wp != null ? wp.getBpCompanyId() : null, ws, removed);
            created++;
        }
        if (created > 0) log.warn("WatchDeadman: {} watch-offline alerts created", created);
    }

    private void fireDeadman(Long personId, Long workPlanId, Long siteId, Long bpCompanyId,
                             WorkerWatchState ws, boolean removed) {
        Person p = persons.findById(personId).orElse(null);
        String who = p != null ? p.getName() : ("작업자 #" + personId);
        String reason = removed ? "워치 미착용 상태로 신호가 30분 이상 없습니다"
                                : "워치 신호가 30분 이상 두절되었습니다";

        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setPersonId(personId);
        a.setWorkPlanId(workPlanId);
        a.setSiteId(siteId);
        a.setBpCompanyId(bpCompanyId);
        a.setKind("watch_offline");
        a.setLevel("warning");
        a.setSeverity(SafetySeverity.CAUTION.name());   // 주의 — 확인 대상(미확인 시 기존 에스컬레이션 루프).
        a.setMessage(who + " — " + reason);
        alertRepo.save(a);
        if (p != null) broadcaster.publishCreated(a, p);   // 관제 WS(보드) 갱신.

        // 데드맨 마커 — 재발 방지 + 수신 재개 시 자동 resolve 대상. watch_state 없으면(미보고) 생성.
        WorkerWatchState state = ws != null ? ws : new WorkerWatchState();
        if (ws == null) {
            state.setPersonId(personId);
            state.setSiteId(siteId);
            state.setBpCompanyId(bpCompanyId);
        }
        state.setDeadmanAlertId(a.getId());
        watchStates.save(state);

        // 본인 폰 확인 요청 FCM(CAUTION) — 열면 SafetyAlertActivity [확인]이 ack POST.
        if (p != null && p.getFcmToken() != null && !p.getFcmToken().isBlank()) {
            fcm.sendSafety(List.of(p.getFcmToken()), "watch_offline", "워치 상태 확인",
                    "워치 신호가 끊겼습니다. 정상이면 확인을 눌러주세요.",
                    SafetySeverity.CAUTION.name(),
                    SafetyAlertClassifier.tts("watch_offline", SafetySeverity.CAUTION), true, a.getId());
        }
        log.warn("WatchDeadman fire person={} site={} removed={} alert={}", personId, siteId, removed, a.getId());
    }
}
