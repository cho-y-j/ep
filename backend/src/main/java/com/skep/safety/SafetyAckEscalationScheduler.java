package com.skep.safety;

import com.skep.field.FieldFcmService;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * S5' 확인응답 미확인 에스컬레이션(§5) — 매분 실행.
 * 작업자 수신 안전알림(강풍·폭염·휴식) 발송 후 5분 경과 + 미확인(ack)이면:
 *   ① 재알림 1회(FCM 재발송, tts 동일) ② escalated_at 기록 ③ BP·공급사 관리자 알림("○○○ 미확인").
 * 재알림은 escalated_at 마커로 1회만(무한 스팸 금지) — 이후는 관제 화면 표시로 관리.
 * 안전 증거 사슬: 발송(created_at) → 미확인 재알림/관제(escalated_at) 시각 기록.
 */
@Component
@RequiredArgsConstructor
public class SafetyAckEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SafetyAckEscalationScheduler.class);

    /** 미확인 판정 기준(분). */
    public static final long ESCALATE_AFTER_MIN = 5;

    /** 작업자에게 발송돼 [확인]을 받아야 하는 kind (워치 응급/낙상 SOS 는 별도 관리 흐름 → 제외). */
    private static final Set<String> ACK_KINDS = Set.of("wind_stop", "heat", "rest");

    private final FieldSafetyAlertRepository alertRepo;
    private final PersonRepository persons;
    private final FieldFcmService fcm;
    private final NotificationService notifications;

    /**
     * 순수 판정(단위 테스트용) — 이 alert 를 지금 에스컬레이션해야 하는가.
     * 미확인·미에스컬레이션·미해결·ack 대상 등급·작업자 수신 kind·생성 5분 경과 전부 충족 시 true.
     */
    public static boolean shouldEscalate(FieldSafetyAlert a, LocalDateTime now, long afterMin) {
        if (a.getAcknowledgedAt() != null) return false;
        if (a.getEscalatedAt() != null) return false;
        if (a.isResolved()) return false;
        if (!SafetySeverity.of(a.getSeverity()).ackRequired()) return false;
        if (!ACK_KINDS.contains(a.getKind())) return false;
        if (a.getCreatedAt() == null) return false;
        return !a.getCreatedAt().isAfter(now.minusMinutes(afterMin));
    }

    @Scheduled(cron = "0 * * * * *")   // 매분.
    @Transactional
    public void escalateUnacknowledged() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(ESCALATE_AFTER_MIN);
        List<FieldSafetyAlert> candidates = alertRepo
                .findBySeverityInAndAcknowledgedAtIsNullAndEscalatedAtIsNullAndResolvedFalseAndCreatedAtBefore(
                        List.of(SafetySeverity.EMERGENCY.name(), SafetySeverity.CAUTION.name()), cutoff);
        int escalated = 0;
        for (FieldSafetyAlert a : candidates) {
            if (!shouldEscalate(a, now, ESCALATE_AFTER_MIN)) continue;   // kind 최종 필터.
            escalateOne(a, now);
            escalated++;
        }
        if (escalated > 0) log.warn("SafetyAckEscalation: {} unacknowledged alerts escalated", escalated);
    }

    private void escalateOne(FieldSafetyAlert a, LocalDateTime now) {
        Person p = persons.findById(a.getPersonId()).orElse(null);
        SafetySeverity sev = SafetySeverity.of(a.getSeverity());
        String tts = SafetyAlertClassifier.tts(a.getKind(), sev);

        // ① 재알림 1회 — 작업자 폰+워치(같은 tts).
        if (p != null) {
            List<String> tokens = new ArrayList<>();
            if (p.getFcmToken() != null && !p.getFcmToken().isBlank()) tokens.add(p.getFcmToken());
            if (p.getWatchFcmToken() != null && !p.getWatchFcmToken().isBlank()) tokens.add(p.getWatchFcmToken());
            if (!tokens.isEmpty()) {
                String body = (a.getMessage() != null && !a.getMessage().isBlank()) ? a.getMessage() : tts;
                fcm.sendSafety(tokens, a.getKind(), "[재알림] 안전 확인 필요", body,
                        sev.name(), tts, sev.ackRequired(), a.getId());
            }
        }

        // ② escalated_at 기록(재알림 1회 마커).
        a.setEscalatedAt(now);
        alertRepo.save(a);

        // ③ BP·공급사 관리자 알림("○○○ 미확인").
        String who = p != null ? p.getName() : ("작업자 #" + a.getPersonId());
        String label = kindLabel(a.getKind());
        String title = label + " 미확인";
        String message = who + " 작업자가 " + label + " 알림을 " + ESCALATE_AFTER_MIN + "분간 확인하지 않았습니다.";
        Set<Long> companies = new LinkedHashSet<>();
        if (a.getBpCompanyId() != null) companies.add(a.getBpCompanyId());
        if (p != null && p.getSupplierId() != null) companies.add(p.getSupplierId());
        for (Long companyId : companies) {
            notifications.sendToCompany(companyId, NotificationType.SAFETY_ACK_MISSING,
                    title, message, "SITE", a.getSiteId(), a.getSiteId());
        }
        log.warn("SafetyAckEscalation alert={} person={} kind={} → re-alert + notify {} companies",
                a.getId(), a.getPersonId(), a.getKind(), companies.size());
    }

    private static String kindLabel(String kind) {
        return switch (kind == null ? "" : kind) {
            case "wind_stop" -> "강풍 작업중지";
            case "heat" -> "폭염";
            case "rest" -> "휴식";
            default -> "안전";
        };
    }
}
