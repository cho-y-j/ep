package com.skep.health;

import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.DailyWorkLogRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.FieldSafetyAlert;
import com.skep.safety.FieldSafetyAlertRepository;
import com.skep.safety.SafetyAlertBroadcaster;
import com.skep.safety.SafetyAlertClassifier;
import com.skep.safety.SafetySeverity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P5-W4 3겹 — 과로 경고(일 1회 크론). 일일 확인서 기반 최근 7일 판정 → 연속 야간 3일+ 또는 60h+ 인
 * 작업자에게 본인 폰 CAUTION(FCM 3등급) + 관리자(BP·공급사) 알림. 인당 일 1회 가드(overwork 경보 존재 확인).
 * 순수 판정은 OverworkEvaluator 로 분리(단위 테스트). "overwork" 는 ACK 에스컬레이션 대상 아님(직접 통보).
 */
@Component
@RequiredArgsConstructor
public class OverworkScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverworkScheduler.class);

    /** field_safety_alerts.kind — 과로 경고(증거 기록 + 인당 일 1회 가드 원천). */
    public static final String KIND = "overwork";
    private static final int WINDOW_DAYS = 7;

    private final DailyWorkLogRepository logs;
    private final PersonRepository persons;
    private final FieldSafetyAlertRepository alertRepo;
    private final com.skep.field.FieldFcmService fcm;
    private final SafetyAlertBroadcaster broadcaster;
    private final NotificationService notifications;

    @Scheduled(cron = "0 0 8 * * *")   // 매일 08:00 — 누적 피로 판정 1회(작업 시작 전후).
    @Transactional
    public void checkOverwork() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(WINDOW_DAYS - 1L);
        List<DailyWorkLog> window = logs.findByWorkDateBetweenOrderByPersonIdAscWorkDateAsc(from, today);
        if (window.isEmpty()) return;

        // 인원별 그룹(장비 전용 로그=personId null 제외).
        Map<Long, List<DailyWorkLog>> byPerson = new LinkedHashMap<>();
        for (DailyWorkLog l : window) {
            if (l.getPersonId() == null) continue;
            byPerson.computeIfAbsent(l.getPersonId(), k -> new ArrayList<>()).add(l);
        }

        LocalDateTime dayStart = today.atStartOfDay();
        int warned = 0;
        for (Map.Entry<Long, List<DailyWorkLog>> e : byPerson.entrySet()) {
            OverworkEvaluator.Verdict v = OverworkEvaluator.evaluate(e.getValue());
            if (!v.triggered()) continue;
            if (alertRepo.existsByPersonIdAndKindAndCreatedAtAfter(e.getKey(), KIND, dayStart)) continue;  // 인당 일 1회.
            fireOverwork(e.getKey(), e.getValue(), v);
            warned++;
        }
        if (warned > 0) log.warn("Overwork: {} workers warned", warned);
    }

    private void fireOverwork(Long personId, List<DailyWorkLog> personLogs, OverworkEvaluator.Verdict v) {
        Person p = persons.findById(personId).orElse(null);
        if (p == null) return;
        DailyWorkLog latest = personLogs.get(personLogs.size() - 1);   // 창은 workDate 오름차순 → 마지막이 최신.
        Long siteId = latest.getSiteId();
        Long bpCompanyId = latest.getBpCompanyId();
        String who = p.getName();
        String reason = String.join(", ", v.reasons());

        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setPersonId(personId);
        a.setSiteId(siteId);
        a.setBpCompanyId(bpCompanyId);
        a.setKind(KIND);
        a.setLevel("warning");
        a.setSeverity(SafetySeverity.CAUTION.name());
        a.setMessage(who + " — 과로 경고(" + reason + ")");
        alertRepo.save(a);
        broadcaster.publishCreated(a, p);

        // 본인 폰 CAUTION(기존 FCM 3등급) — 열면 [확인] 이 ack POST.
        if (p.getFcmToken() != null && !p.getFcmToken().isBlank()) {
            fcm.sendSafety(List.of(p.getFcmToken()), KIND, "과로 경고",
                    "최근 근무가 많습니다(" + reason + "). 충분히 쉬세요.",
                    SafetySeverity.CAUTION.name(),
                    SafetyAlertClassifier.tts(KIND, SafetySeverity.CAUTION), true, a.getId());
        }

        // 관리자 알림(BP·공급사) — 배치·휴식 조정 검토.
        Set<Long> companies = new LinkedHashSet<>();
        if (bpCompanyId != null) companies.add(bpCompanyId);
        if (p.getSupplierId() != null) companies.add(p.getSupplierId());
        String title = "과로 경고 — " + who;
        String message = who + " 작업자 " + reason + " — 배치·휴식 조정을 검토하세요.";
        for (Long companyId : companies) {
            notifications.sendToCompany(companyId, NotificationType.OVERWORK_WARNING, title, message,
                    "SITE", siteId, siteId, "시스템 (과로 경고)");
        }
        log.warn("Overwork fire person={} site={} nights={} weekHours={}",
                personId, siteId, v.consecutiveNights(), Math.round(v.weekHours()));
    }
}
