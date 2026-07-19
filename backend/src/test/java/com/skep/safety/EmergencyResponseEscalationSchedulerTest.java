package com.skep.safety;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5-W2 60초 무응답 확대 순수 판정 — 통보(peer_notified) 후 60초 경과·미응답·미확대·미해결.
 * 응답(first_response) 또는 확대(peer_escalated) 또는 해결됐으면 확대 안 함(1회만).
 */
class EmergencyResponseEscalationSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 12, 0, 0);
    private static final long AFTER = EmergencyResponseEscalationScheduler.EXPAND_AFTER_SEC;

    private FieldSafetyAlert notified(LocalDateTime peerNotifiedAt) {
        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setKind("emergency");
        a.setSeverity("EMERGENCY");
        a.setPeerNotifiedAt(peerNotifiedAt);
        return a;
    }

    @Test
    void noResponseAfter60sExpands() {
        assertTrue(EmergencyResponseEscalationScheduler.shouldExpand(notified(NOW.minusSeconds(61)), NOW, AFTER));
    }

    @Test
    void exactlyAt60sExpands() {
        assertTrue(EmergencyResponseEscalationScheduler.shouldExpand(notified(NOW.minusSeconds(60)), NOW, AFTER));
    }

    @Test
    void before60sDoesNotExpand() {
        assertFalse(EmergencyResponseEscalationScheduler.shouldExpand(notified(NOW.minusSeconds(59)), NOW, AFTER));
    }

    @Test
    void notNotifiedDoesNotExpand() {
        // 대응체인 미발동(peer_notified 없음) → 확대 대상 아님.
        assertFalse(EmergencyResponseEscalationScheduler.shouldExpand(notified(null), NOW, AFTER));
    }

    @Test
    void respondedDoesNotExpand() {
        FieldSafetyAlert a = notified(NOW.minusSeconds(120));
        a.setFirstResponseAt(NOW.minusSeconds(30));   // 이미 [제가 갑니다] 응답.
        assertFalse(EmergencyResponseEscalationScheduler.shouldExpand(a, NOW, AFTER));
    }

    @Test
    void alreadyExpandedDoesNotExpandAgain() {
        FieldSafetyAlert a = notified(NOW.minusSeconds(120));
        a.setPeerEscalatedAt(NOW.minusSeconds(30));   // 확대는 1회만.
        assertFalse(EmergencyResponseEscalationScheduler.shouldExpand(a, NOW, AFTER));
    }

    @Test
    void resolvedDoesNotExpand() {
        FieldSafetyAlert a = notified(NOW.minusSeconds(120));
        a.setResolved(true);
        assertFalse(EmergencyResponseEscalationScheduler.shouldExpand(a, NOW, AFTER));
    }
}
