package com.skep.safety;

import com.skep.safety.dto.SafetyReportDtos.AlertSummary;
import com.skep.safety.dto.SafetyReportDtos.EmergencyResponseSummary;
import com.skep.safety.dto.SafetyReportDtos.EmergencyTimeline;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P3d 이행 보고서 순수 계산(단위 테스트 대상) — 안전알림 확인율·평균 확인 소요.
 * ack 대상 판정은 에스컬레이션(SafetyAckEscalationScheduler)과 동일 규칙:
 * ack 필요 등급(EMERGENCY/CAUTION) + 작업자 수신 kind(강풍·폭염·휴식).
 */
public final class SafetyReportCalculator {

    private SafetyReportCalculator() {}

    /** 작업자에게 [확인]을 받아야 하는 kind (= SafetyAckEscalationScheduler.ACK_KINDS). SOS/낙상은 관리자 응답 흐름이라 제외. */
    static final Set<String> ACK_KINDS = Set.of("wind_stop", "heat", "rest");

    /** 이 알림이 [확인] 응답 대상인가(고지→확인 사슬 집계 분모). */
    public static boolean subjectToAck(FieldSafetyAlert a) {
        return SafetySeverity.of(a.getSeverity()).ackRequired() && ACK_KINDS.contains(a.getKind());
    }

    /** 발송→확인 소요(초). 확인 대상이 아니거나 미확인·시각 결측이면 null. */
    public static Long ackElapsedSeconds(FieldSafetyAlert a) {
        if (!subjectToAck(a)) return null;
        if (a.getCreatedAt() == null || a.getAcknowledgedAt() == null) return null;
        long sec = Duration.between(a.getCreatedAt(), a.getAcknowledgedAt()).getSeconds();
        return sec < 0 ? 0 : sec;
    }

    /** 기간 내 알림들의 요약(총·확인대상·확인·확인율·평균확인소요분·에스컬 건수). */
    public static AlertSummary alertSummary(List<FieldSafetyAlert> alerts) {
        int total = alerts.size();
        int ackNeeded = 0;
        int acknowledged = 0;
        int escalated = 0;
        long ackSecondsSum = 0;
        int ackedForAvg = 0;
        for (FieldSafetyAlert a : alerts) {
            if (a.getEscalatedAt() != null) escalated++;
            if (!subjectToAck(a)) continue;
            ackNeeded++;
            Long elapsed = ackElapsedSeconds(a);
            if (elapsed != null) {
                acknowledged++;
                ackSecondsSum += elapsed;
                ackedForAvg++;
            }
        }
        Integer ackRatePct = ackNeeded == 0 ? null : (int) Math.round(acknowledged * 100.0 / ackNeeded);
        Double avgAckMinutes = ackedForAvg == 0 ? null
                : Math.round((ackSecondsSum / (double) ackedForAvg) / 60.0 * 10.0) / 10.0;
        return new AlertSummary(total, ackNeeded, acknowledged, ackRatePct, avgAckMinutes, escalated);
    }

    /** 개인 응급(대응체인 대상) kind — SOS/낙상/BLE 릴레이 생성. 강풍·폭염(현장 단위)은 제외. */
    static final Set<String> EMERGENCY_KINDS = Set.of("emergency", "fall", "fall_detected");

    public static boolean isEmergencyChain(FieldSafetyAlert a) {
        return EMERGENCY_KINDS.contains(a.getKind() == null ? "" : a.getKind());
    }

    private static Long elapsed(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) return null;
        long sec = Duration.between(from, to).getSeconds();
        return sec < 0 ? 0 : sec;
    }

    /**
     * P5-W2/W3 긴급 대응 이력 — 개인 긴급 경보별 골든타임 사슬 + 평균 최초응답시간.
     * responderCountByAlert = alertId→응답자 수(서비스가 배치 조회로 주입).
     */
    public static EmergencyResponseSummary emergencyResponseSummary(
            List<FieldSafetyAlert> alerts, Map<Long, Integer> responderCountByAlert) {
        List<EmergencyTimeline> timelines = new ArrayList<>();
        int chainActivated = 0, responded = 0, relayedCount = 0, escalatedCount = 0;
        long respSecSum = 0;
        int respForAvg = 0;
        for (FieldSafetyAlert a : alerts) {
            if (!isEmergencyChain(a)) continue;
            boolean relayed = a.getRelayedAt() != null;
            boolean escalated = a.getPeerEscalatedAt() != null;
            if (a.getPeerNotifiedAt() != null) chainActivated++;
            if (relayed) relayedCount++;
            if (escalated) escalatedCount++;
            Long responseElapsed = elapsed(a.getPeerNotifiedAt(), a.getFirstResponseAt());
            if (a.getFirstResponseAt() != null) {
                responded++;
                if (responseElapsed != null) { respSecSum += responseElapsed; respForAvg++; }
            }
            timelines.add(new EmergencyTimeline(
                    a.getId(), a.getKind(), kindLabel(a.getKind()),
                    a.getCreatedAt(), a.getPeerNotifiedAt(), a.getFirstResponseAt(), a.getResolvedAt(),
                    elapsed(a.getCreatedAt(), a.getPeerNotifiedAt()),
                    responseElapsed,
                    elapsed(a.getCreatedAt(), a.getResolvedAt()),
                    responderCountByAlert.getOrDefault(a.getId(), 0),
                    relayed, escalated));
        }
        Double avgFirstResponse = respForAvg == 0 ? null
                : Math.round((respSecSum / (double) respForAvg) * 10.0) / 10.0;
        return new EmergencyResponseSummary(
                timelines.size(), chainActivated, responded, avgFirstResponse,
                relayedCount, escalatedCount, timelines);
    }

    /** kind → 한글 라벨(타임라인·미이행 표시). */
    public static String kindLabel(String kind) {
        return switch (kind == null ? "" : kind) {
            case "wind_stop" -> "강풍 작업중지";
            case "heat" -> "폭염";
            case "rest" -> "휴식";
            case "emergency" -> "응급(SOS)";
            case "fall", "fall_detected" -> "낙상 감지";
            default -> "안전알림";
        };
    }
}
