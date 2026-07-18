package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.field.FieldFcmService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 안전알림 생성/해결 시 WebSocket 발행 + 같은 현장 다른 작업자에게 FCM 전파. */
@Component
@RequiredArgsConstructor
public class SafetyAlertBroadcaster {

    private final SimpMessagingTemplate stomp;
    private final PersonRepository persons;
    private final FieldFcmService fcm;
    private final AttendanceSessionRepository attendanceSessions;
    private final WorkPlanRepository workPlans;

    public void publishCreated(FieldSafetyAlert a, Person sender) {
        publishWs(createdPayload(a, sender), a.getSiteId(), a.getBpCompanyId());

        // 2) FCM — 같은 현장(site) 작업자 폰에만 푸시. (전 회사 작업자에게 누설 방지)
        if (a.getSiteId() != null && "danger".equals(a.getLevel())) {
            Set<Long> sameSitePersonIds = sameSitePersonIds(a.getSiteId(), sender.getId());
            List<String> tokens = sameSitePersonIds.isEmpty() ? List.of()
                    : persons.findByFcmTokenIsNotNullAndIdIn(sameSitePersonIds).stream()
                    .map(Person::getFcmToken)
                    .filter(Objects::nonNull)
                    .filter(t -> !t.isBlank())
                    .toList();
            String title = sender.getName() + " 긴급 알림";
            String body = (a.getMessage() != null && !a.getMessage().isBlank())
                    ? a.getMessage() : "현장에서 긴급 상황이 발생했습니다";
            fcm.sendAnnouncement(tokens, title, body);
        }

        // 3) 휴식/폭염 알림 — 해당 작업자 '본인'의 폰+워치로 push (대시보드는 위 STOMP 로 관리자 수신).
        //    S5' 3등급 발송: severity/tts/ack_required/alert_id 를 실어 폰앱이 읽어주기·확인응답 처리.
        if ("rest".equals(a.getKind()) || "heat".equals(a.getKind())) {
            List<String> own = new java.util.ArrayList<>();
            if (sender.getFcmToken() != null && !sender.getFcmToken().isBlank()) own.add(sender.getFcmToken());
            if (sender.getWatchFcmToken() != null && !sender.getWatchFcmToken().isBlank()) own.add(sender.getWatchFcmToken());
            if (!own.isEmpty()) {
                String body = (a.getMessage() != null && !a.getMessage().isBlank())
                        ? a.getMessage() : "휴식이 필요합니다";
                SafetySeverity sev = SafetySeverity.of(a.getSeverity());
                fcm.sendSafety(own, a.getKind(), "휴식 권고", body,
                        sev.name(), SafetyAlertClassifier.tts(a.getKind(), sev), sev.ackRequired(), a.getId());
            }
        }
    }

    /** S5' 확인응답 — 작업자 [확인] 시 관제(WS) 실시간 갱신. */
    public void publishAcked(FieldSafetyAlert a) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "acked");
        payload.put("id", a.getId());
        payload.put("acknowledged_at", a.getAcknowledgedAt());
        payload.put("ack_person_id", a.getAckPersonId());
        publishWs(payload, a.getSiteId(), a.getBpCompanyId());
    }

    public void publishResolved(FieldSafetyAlert a) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "resolved");
        payload.put("id", a.getId());
        publishWs(payload, a.getSiteId(), a.getBpCompanyId());
    }

    /**
     * S1 강풍 작업중지 경보 — 현장 단위 1회 발송. 관제(WS)는 작업자별 alert 로 전파,
     * FCM 은 해당 현장 작업자 전원 폰+워치로 한 번만 배치(중복 방지). publishCreated 의 danger 재전파 미사용.
     */
    public void publishWindStop(List<FieldSafetyAlert> alerts, Map<Long, Person> personById, String body) {
        String safeBody = (body != null && !body.isBlank()) ? body : "강풍 기준 초과 — 작업을 중지하세요";
        for (FieldSafetyAlert a : alerts) {
            Person p = personById.get(a.getPersonId());
            if (p == null) continue;
            publishWs(createdPayload(a, p), a.getSiteId(), a.getBpCompanyId());
            // S5' 확인응답: 작업자별 자기 alert_id 를 실어 발송(본인 알림만 [확인] 가능하도록).
            List<String> tokens = new java.util.ArrayList<>();
            if (p.getFcmToken() != null && !p.getFcmToken().isBlank()) tokens.add(p.getFcmToken());
            if (p.getWatchFcmToken() != null && !p.getWatchFcmToken().isBlank()) tokens.add(p.getWatchFcmToken());
            if (tokens.isEmpty()) continue;
            SafetySeverity sev = SafetySeverity.of(a.getSeverity());
            fcm.sendSafety(tokens, "wind_stop", "강풍 작업중지", safeBody,
                    sev.name(), SafetyAlertClassifier.tts("wind_stop", sev), sev.ackRequired(), a.getId());
        }
    }

    /** created 이벤트 WS payload — 관제(SafetyAlertsPage) 가 기대하는 작업자 중심 스키마. */
    private Map<String, Object> createdPayload(FieldSafetyAlert a, Person sender) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "created");
        payload.put("id", a.getId());
        payload.put("person_id", a.getPersonId());
        payload.put("person_name", sender.getName());
        payload.put("person_phone", sender.getPhone());
        payload.put("person_has_photo", sender.getPhotoKey() != null);
        payload.put("kind", a.getKind());
        payload.put("level", a.getLevel());
        payload.put("severity", a.getSeverity());
        payload.put("message", a.getMessage());
        payload.put("hr", a.getHr());
        payload.put("spo2", a.getSpo2());
        payload.put("lat", a.getLat());
        payload.put("lng", a.getLng());
        payload.put("site_id", a.getSiteId());
        payload.put("created_at", a.getCreatedAt());
        return payload;
    }

    /** ADMIN(/all)·BP(company-)·현장(site-) 토픽으로 발행. */
    private void publishWs(Map<String, Object> payload, Long siteId, Long bpCompanyId) {
        if (siteId != null) {
            stomp.convertAndSend("/topic/safety-alerts/site-" + siteId, payload);
        }
        if (bpCompanyId != null) {
            stomp.convertAndSend("/topic/safety-alerts/company-" + bpCompanyId, payload);
        }
        stomp.convertAndSend("/topic/safety-alerts/all", payload);
    }

    /** 같은 site_id 의 작업자 person id — 오늘 출근 기록 기준 (발신자 본인 제외). dispatchHelp 와 동일 로직. */
    private Set<Long> sameSitePersonIds(Long siteId, Long excludePersonId) {
        Set<Long> ids = new HashSet<>();
        var todayStart = java.time.LocalDate.now().atStartOfDay();
        for (AttendanceSession s : attendanceSessions.findByCheckInAtGreaterThanEqual(todayStart)) {
            if (s.getWorkPlanId() == null) continue;
            WorkPlan wp = workPlans.findById(s.getWorkPlanId()).orElse(null);
            if (wp != null && Objects.equals(wp.getSiteId(), siteId)) {
                ids.add(s.getPersonId());
            }
        }
        ids.remove(excludePersonId);
        return ids;
    }
}
