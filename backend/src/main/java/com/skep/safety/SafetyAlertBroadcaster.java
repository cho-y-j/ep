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
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "created");
        payload.put("id", a.getId());
        payload.put("person_id", a.getPersonId());
        payload.put("person_name", sender.getName());
        payload.put("person_phone", sender.getPhone());
        payload.put("person_has_photo", sender.getPhotoKey() != null);
        payload.put("kind", a.getKind());
        payload.put("level", a.getLevel());
        payload.put("message", a.getMessage());
        payload.put("hr", a.getHr());
        payload.put("spo2", a.getSpo2());
        payload.put("lat", a.getLat());
        payload.put("lng", a.getLng());
        payload.put("site_id", a.getSiteId());
        payload.put("created_at", a.getCreatedAt());

        // 1) ADMIN/BP 대시보드 — ADMIN 은 /all, BP 는 자기 회사(company-) topic 구독. site- 는 ADMIN 세부 뷰.
        if (a.getSiteId() != null) {
            stomp.convertAndSend("/topic/safety-alerts/site-" + a.getSiteId(), payload);
        }
        if (a.getBpCompanyId() != null) {
            stomp.convertAndSend("/topic/safety-alerts/company-" + a.getBpCompanyId(), payload);
        }
        stomp.convertAndSend("/topic/safety-alerts/all", payload);

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
        if ("rest".equals(a.getKind()) || "heat".equals(a.getKind())) {
            List<String> own = new java.util.ArrayList<>();
            if (sender.getFcmToken() != null && !sender.getFcmToken().isBlank()) own.add(sender.getFcmToken());
            if (sender.getWatchFcmToken() != null && !sender.getWatchFcmToken().isBlank()) own.add(sender.getWatchFcmToken());
            if (!own.isEmpty()) {
                String body = (a.getMessage() != null && !a.getMessage().isBlank())
                        ? a.getMessage() : "휴식이 필요합니다";
                fcm.sendTyped(own, a.getKind(), "휴식 권고", body);
            }
        }
    }

    public void publishResolved(FieldSafetyAlert a) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "resolved");
        payload.put("id", a.getId());
        if (a.getSiteId() != null) {
            stomp.convertAndSend("/topic/safety-alerts/site-" + a.getSiteId(), payload);
        }
        if (a.getBpCompanyId() != null) {
            stomp.convertAndSend("/topic/safety-alerts/company-" + a.getBpCompanyId(), payload);
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
