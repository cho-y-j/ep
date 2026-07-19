package com.skep.safety;

import com.skep.safety.dto.SafetyReportDtos.AlertSummary;
import com.skep.safety.dto.SafetyReportDtos.EmergencyResponseSummary;
import com.skep.safety.dto.SafetyReportDtos.EmergencyTimeline;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3d 이행 보고서 순수 계산 — 확인율·평균 확인 소요·확인 대상 판정. */
class SafetyReportCalculatorTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 18, 9, 0, 0);

    private FieldSafetyAlert alert(String kind, String severity, LocalDateTime createdAt, LocalDateTime ackedAt) {
        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setKind(kind);
        a.setSeverity(severity);
        a.setCreatedAt(createdAt);
        a.setAcknowledgedAt(ackedAt);
        return a;
    }

    @Test
    void subjectToAck_ackKindWithAckSeverity() {
        assertTrue(SafetyReportCalculator.subjectToAck(alert("wind_stop", "EMERGENCY", T, null)));
        assertTrue(SafetyReportCalculator.subjectToAck(alert("heat", "CAUTION", T, null)));
        assertTrue(SafetyReportCalculator.subjectToAck(alert("rest", "CAUTION", T, null)));
    }

    @Test
    void subjectToAck_excludesNonAckKindAndNormalSeverity() {
        // SOS/낙상은 관리자 응답 흐름 → ack 대상 아님.
        assertFalse(SafetyReportCalculator.subjectToAck(alert("emergency", "EMERGENCY", T, null)));
        // NORMAL/레거시 등급은 확인 불요.
        assertFalse(SafetyReportCalculator.subjectToAck(alert("heat", "NORMAL", T, null)));
        assertFalse(SafetyReportCalculator.subjectToAck(alert("heat", null, T, null)));
    }

    @Test
    void ackElapsedSeconds_computedForAckedSubject() {
        // 09:00 발송 → 09:03:20 확인 = 200초.
        FieldSafetyAlert a = alert("wind_stop", "EMERGENCY", T, T.plusSeconds(200));
        assertEquals(200L, SafetyReportCalculator.ackElapsedSeconds(a));
    }

    @Test
    void ackElapsedSeconds_nullWhenNotAckedOrNotSubject() {
        assertNull(SafetyReportCalculator.ackElapsedSeconds(alert("wind_stop", "EMERGENCY", T, null)));
        assertNull(SafetyReportCalculator.ackElapsedSeconds(alert("emergency", "EMERGENCY", T, T.plusSeconds(60))));
    }

    @Test
    void alertSummary_ackRateAndAvgElapsed() {
        // 확인 대상 3건: 2건 확인(60초·180초), 1건 미확인 → 확인율 67%, 평균 (60+180)/2=120초=2.0분.
        // + ack 대상 아닌 알림 1건(SOS) → total 4, ackNeeded 3.
        List<FieldSafetyAlert> alerts = List.of(
                alert("wind_stop", "EMERGENCY", T, T.plusSeconds(60)),
                alert("heat", "CAUTION", T, T.plusSeconds(180)),
                alert("rest", "CAUTION", T, null),
                alert("emergency", "EMERGENCY", T, null));
        AlertSummary s = SafetyReportCalculator.alertSummary(alerts);
        assertEquals(4, s.total());
        assertEquals(3, s.ackNeeded());
        assertEquals(2, s.acknowledged());
        assertEquals(67, s.ackRatePct());   // round(2/3*100)=67
        assertEquals(2.0, s.avgAckMinutes());
        assertEquals(0, s.escalatedCount());
    }

    @Test
    void alertSummary_escalatedCountedAndRateNullWhenNoAckTarget() {
        FieldSafetyAlert escalated = alert("wind_stop", "EMERGENCY", T, null);
        escalated.setEscalatedAt(T.plusMinutes(5));
        FieldSafetyAlert sos = alert("emergency", "EMERGENCY", T, null);   // ack 대상 아님.
        AlertSummary s = SafetyReportCalculator.alertSummary(List.of(sos));
        assertEquals(1, s.total());
        assertEquals(0, s.ackNeeded());
        assertNull(s.ackRatePct());
        assertNull(s.avgAckMinutes());

        AlertSummary s2 = SafetyReportCalculator.alertSummary(List.of(escalated, sos));
        assertEquals(1, s2.escalatedCount());
        assertEquals(1, s2.ackNeeded());
        assertEquals(0, s2.ackRatePct());   // 0/1
    }

    // ── P5-W2/W3 긴급 대응 이력 ──────────────────────────────────

    @Test
    void isEmergencyChain_onlyIndividualCollapseKinds() {
        assertTrue(SafetyReportCalculator.isEmergencyChain(alert("emergency", "EMERGENCY", T, null)));
        assertTrue(SafetyReportCalculator.isEmergencyChain(alert("fall", "EMERGENCY", T, null)));
        assertTrue(SafetyReportCalculator.isEmergencyChain(alert("fall_detected", "EMERGENCY", T, null)));
        // 현장 단위(강풍·폭염)·데드맨은 대응체인 대상 아님.
        assertFalse(SafetyReportCalculator.isEmergencyChain(alert("wind_stop", "EMERGENCY", T, null)));
        assertFalse(SafetyReportCalculator.isEmergencyChain(alert("heat", "CAUTION", T, null)));
        assertFalse(SafetyReportCalculator.isEmergencyChain(alert("watch_offline", "CAUTION", T, null)));
    }

    @Test
    void emergencyResponseSummary_goldenTimeChainAndAverage() {
        // A: 응급 — 통보 +10s, 최초응답 +40s(응답소요 30s), 해제 +300s.
        FieldSafetyAlert a = alert("emergency", "EMERGENCY", T, null);
        a.setId(1L);
        a.setPeerNotifiedAt(T.plusSeconds(10));
        a.setFirstResponseAt(T.plusSeconds(40));
        a.setResolvedAt(T.plusSeconds(300));
        a.setResolved(true);
        // B: 낙상 — 통보 +5s, 무응답, 60초 확대(peer_escalated).
        FieldSafetyAlert b = alert("fall", "EMERGENCY", T, null);
        b.setId(2L);
        b.setPeerNotifiedAt(T.plusSeconds(5));
        b.setPeerEscalatedAt(T.plusSeconds(65));
        // C: 릴레이 생성 응급 — 통보 +2s, 최초응답 +50s(응답소요 48s), 릴레이 수신.
        FieldSafetyAlert c = alert("emergency", "EMERGENCY", T, null);
        c.setId(3L);
        c.setPeerNotifiedAt(T.plusSeconds(2));
        c.setFirstResponseAt(T.plusSeconds(50));
        c.setRelayedAt(T.plusSeconds(1));
        // D: 강풍 — 대응체인 대상 아님(제외).
        FieldSafetyAlert d = alert("wind_stop", "EMERGENCY", T, null);
        d.setId(4L);

        Map<Long, Integer> counts = Map.of();   // 응답자 수 미주입 → 전부 0(이 테스트는 미검증).
        EmergencyResponseSummary s = SafetyReportCalculator.emergencyResponseSummary(List.of(a, b, c, d), counts);

        assertEquals(3, s.total());            // A,B,C (D 제외).
        assertEquals(3, s.chainActivated());   // 셋 다 통보됨.
        assertEquals(2, s.responded());        // A,C.
        assertEquals(39.0, s.avgFirstResponseSeconds());   // (30+48)/2.
        assertEquals(1, s.relayedCount());     // C.
        assertEquals(1, s.escalatedCount());   // B.
        assertEquals(3, s.timelines().size());
    }

    @Test
    void emergencyResponseSummary_elapsedAndResponderCount() {
        FieldSafetyAlert a = alert("emergency", "EMERGENCY", T, null);
        a.setId(7L);
        a.setPeerNotifiedAt(T.plusSeconds(10));
        a.setFirstResponseAt(T.plusSeconds(40));
        a.setResolvedAt(T.plusSeconds(300));
        EmergencyResponseSummary s = SafetyReportCalculator.emergencyResponseSummary(
                List.of(a), Map.of(7L, 2));
        EmergencyTimeline t = s.timelines().get(0);
        assertEquals(10L, t.notifyElapsedSeconds());     // 감지→통보.
        assertEquals(30L, t.responseElapsedSeconds());   // 통보→최초응답.
        assertEquals(300L, t.resolveElapsedSeconds());   // 감지→해제.
        assertEquals(2, t.responderCount());
    }

    @Test
    void emergencyResponseSummary_emptyWhenNoEmergencies() {
        EmergencyResponseSummary s = SafetyReportCalculator.emergencyResponseSummary(
                List.of(alert("heat", "CAUTION", T, null)), Map.of());
        assertEquals(0, s.total());
        assertNull(s.avgFirstResponseSeconds());
        assertTrue(s.timelines().isEmpty());
    }
}
