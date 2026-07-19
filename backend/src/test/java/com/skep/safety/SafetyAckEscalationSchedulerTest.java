package com.skep.safety;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S5' 미확인 에스컬레이션 순수 판정(5분 경과·미확인·미에스컬·미해결·ack 대상 등급·작업자 수신 kind). */
class SafetyAckEscalationSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 18, 12, 0, 0);
    private static final long AFTER = SafetyAckEscalationScheduler.ESCALATE_AFTER_MIN;

    private FieldSafetyAlert alert(String kind, String severity, LocalDateTime createdAt) {
        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setKind(kind);
        a.setSeverity(severity);
        a.setCreatedAt(createdAt);
        return a;
    }

    @Test
    void unacknowledgedAfter5MinEscalates() {
        FieldSafetyAlert a = alert("wind_stop", "EMERGENCY", NOW.minusMinutes(6));
        assertTrue(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }

    @Test
    void exactlyAtThresholdEscalates() {
        FieldSafetyAlert a = alert("rest", "CAUTION", NOW.minusMinutes(5));
        assertTrue(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }

    @Test
    void beforeThresholdDoesNotEscalate() {
        FieldSafetyAlert a = alert("heat", "CAUTION", NOW.minusMinutes(3));
        assertFalse(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }

    @Test
    void acknowledgedDoesNotEscalate() {
        FieldSafetyAlert a = alert("wind_stop", "EMERGENCY", NOW.minusMinutes(10));
        a.setAcknowledgedAt(NOW.minusMinutes(1));
        assertFalse(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }

    @Test
    void alreadyEscalatedDoesNotEscalateAgain() {
        FieldSafetyAlert a = alert("wind_stop", "EMERGENCY", NOW.minusMinutes(10));
        a.setEscalatedAt(NOW.minusMinutes(2));
        assertFalse(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));   // 재알림 1회만.
    }

    @Test
    void resolvedDoesNotEscalate() {
        FieldSafetyAlert a = alert("wind_stop", "EMERGENCY", NOW.minusMinutes(10));
        a.setResolved(true);
        assertFalse(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }

    @Test
    void normalSeverityDoesNotEscalate() {
        FieldSafetyAlert a = alert("heat", "NORMAL", NOW.minusMinutes(10));
        assertFalse(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }

    @Test
    void sosKindNotSubjectToAckEscalation() {
        // 워치 응급/낙상은 관리자 응답(resolve/주변 호출) 흐름 → ack 에스컬레이션 제외.
        FieldSafetyAlert a = alert("emergency", "EMERGENCY", NOW.minusMinutes(30));
        assertFalse(SafetyAckEscalationScheduler.shouldEscalate(a, NOW, AFTER));
    }
}
