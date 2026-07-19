package com.skep.safety;

import com.skep.person.Person;
import com.skep.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P5-W2 긴급 대응체인 60초 무응답 확대 — 30초 주기 실행(기존 스케줄러 패턴).
 * 근접 동료 통보(peer_notified) 후 60초 내 [제가 갑니다] 응답이 하나도 없으면:
 *   현장 전체 동료로 확대 + BP·공급사 관리자 통보 + peer_escalated_at(1회 마커). 실행은 EmergencyResponseService.
 */
@Component
@RequiredArgsConstructor
public class EmergencyResponseEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmergencyResponseEscalationScheduler.class);

    /** 무응답 확대 기준(초). */
    public static final long EXPAND_AFTER_SEC = 60;

    private final FieldSafetyAlertRepository alertRepo;
    private final PersonRepository persons;
    private final EmergencyResponseService responseService;

    /**
     * 순수 판정(단위 테스트용) — 이 경보를 지금 확대해야 하는가.
     * 대응체인 발동(peer_notified)됐고 미응답·미확대·미해결·통보 60초 경과 전부 충족 시 true.
     */
    public static boolean shouldExpand(FieldSafetyAlert a, LocalDateTime now, long afterSec) {
        if (a.getPeerNotifiedAt() == null) return false;
        if (a.getFirstResponseAt() != null) return false;   // 이미 응답 있음.
        if (a.getPeerEscalatedAt() != null) return false;   // 이미 확대(1회만).
        if (a.isResolved()) return false;
        return !a.getPeerNotifiedAt().isAfter(now.minusSeconds(afterSec));
    }

    @Scheduled(cron = "*/30 * * * * *")   // 30초마다(60초 임계 판정).
    @Transactional
    public void expandUnresponded() {
        LocalDateTime now = LocalDateTime.now();
        List<FieldSafetyAlert> candidates = alertRepo
                .findByPeerNotifiedAtIsNotNullAndFirstResponseAtIsNullAndPeerEscalatedAtIsNullAndResolvedFalse();
        int expanded = 0;
        for (FieldSafetyAlert a : candidates) {
            if (!shouldExpand(a, now, EXPAND_AFTER_SEC)) continue;
            Person victim = persons.findById(a.getPersonId()).orElse(null);
            if (victim == null) {   // 방어 — 피재자 삭제 시 마커만 찍어 무한 재조회 방지.
                a.setPeerEscalatedAt(now);
                alertRepo.save(a);
                continue;
            }
            responseService.expandPeers(a, victim);
            expanded++;
        }
        if (expanded > 0) log.warn("EmergencyResponseEscalation: {} emergencies expanded (60s no-response)", expanded);
    }
}
