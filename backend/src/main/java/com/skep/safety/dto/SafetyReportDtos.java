package com.skep.safety.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P3d 안전관리 이행 보고서(증거사슬 출력물) — 사고·감사 시 "고지·확인·조치 이력"을 현장·기간별 일괄 제출.
 * 전부 기존 데이터 실사 집계(신규 저장 없음). 개인 서명 이미지는 유무/카운트만(개인정보 최소화).
 */
public final class SafetyReportDtos {

    private SafetyReportDtos() {}

    public record SafetyReport(
            Long siteId,
            String siteName,
            String siteCode,
            String bpCompanyName,
            String clientOrgName,
            LocalDate from,
            LocalDate to,
            LocalDateTime generatedAt,
            String generatedBy,
            AlertSummary alertSummary,
            InspectionSummary inspectionSummary,
            WorkComplianceSummary workComplianceSummary,
            EmergencyResponseSummary emergencyResponse,
            List<TimelineDay> timeline,
            Noncompliance noncompliance,
            SiteSafetySettingsResponse standard
    ) {}

    /**
     * P5-W2/W3 긴급 대응 이력 — 개인 응급(SOS/낙상/BLE 릴레이) 골든타임 요약.
     * avgFirstResponseSeconds = 통보→최초응답 평균(응답 있은 것만, 없으면 null).
     */
    public record EmergencyResponseSummary(
            int total,                       // 기간 내 개인 긴급 경보 수.
            int chainActivated,              // 대응체인 발동(근접 동료 통보) 수.
            int responded,                   // [제가 갑니다] 응답 있은 수.
            Double avgFirstResponseSeconds,  // 통보→최초응답 평균(초).
            int relayedCount,                // BLE 대리중계 수신 수.
            int escalatedCount,              // 60초 무응답 → 현장 확대 수.
            List<EmergencyTimeline> timelines
    ) {}

    /** 긴급 1건의 골든타임 사슬 — 감지(created)→통보→최초응답→해제. elapsed 는 결측 시 null. */
    public record EmergencyTimeline(
            Long alertId,
            String kind,
            String kindLabel,
            LocalDateTime detectedAt,
            LocalDateTime peerNotifiedAt,
            LocalDateTime firstResponseAt,
            LocalDateTime resolvedAt,
            Long notifyElapsedSeconds,     // 감지→통보.
            Long responseElapsedSeconds,   // 통보→최초응답.
            Long resolveElapsedSeconds,    // 감지→해제.
            int responderCount,
            boolean relayed,
            boolean escalated
    ) {}

    /** ①요약 — 안전알림 고지→확인 사슬. avgAckMinutes/ackRatePct 는 대상(ackNeeded) 0 이면 null. */
    public record AlertSummary(
            int total,
            int ackNeeded,
            int acknowledged,
            Integer ackRatePct,
            Double avgAckMinutes,
            int escalatedCount
    ) {}

    /** ①요약 — 점검(법정 NFC · 조종원 일일). */
    public record InspectionSummary(
            int legalTotal,
            int legalDays,
            int nfcVerified,
            Integer nfcRatePct,
            int operatorTotal,
            int operatorDays,
            int targetEquipment
    ) {}

    /** ①요약 — 계획서 서명 완결 · 일일 확인서 서명율. */
    public record WorkComplianceSummary(
            int planTotal,
            int planFullySigned,
            int logTotal,
            int logSigned,
            Integer logSignRatePct
    ) {}

    /** ②일자별 타임라인 — 그 날의 알림·점검·강풍 이벤트. */
    public record TimelineDay(
            LocalDate date,
            List<TimelineAlert> alerts,
            int legalInspections,
            int operatorInspections,
            WindEvent windEvent
    ) {}

    /** 알림 1건의 고지→확인 사슬. ackElapsedSeconds = 발송→확인 소요(미확인이면 null). */
    public record TimelineAlert(
            Long id,
            String kind,
            String kindLabel,
            String severity,
            String level,
            LocalDateTime createdAt,
            LocalDateTime acknowledgedAt,
            Long ackElapsedSeconds,
            LocalDateTime escalatedAt,
            boolean resolved,
            boolean needsAck
    ) {}

    /** 강풍 작업중지 진입/해제(현장 스냅샷 1건 — 최근 전이만 기록됨, 정직 표기). */
    public record WindEvent(
            LocalDateTime enteredAt,
            LocalDateTime clearedAt,
            Double windMps
    ) {}

    /** ③미이행 목록. */
    public record Noncompliance(
            List<TimelineAlert> unacknowledgedAlerts,
            List<LocalDate> uninspectedWorkDays,
            List<UnsignedPlan> unsignedPlans,
            List<UnsignedLog> unsignedLogs
    ) {}

    /** 서명 미완결 계획서 — pendingRoles = 아직 SIGNED 아닌 5역할(한글 라벨). */
    public record UnsignedPlan(
            Long workPlanId,
            LocalDate workDate,
            String title,
            List<String> pendingRoles
    ) {}

    /** 미서명 일일 확인서. */
    public record UnsignedLog(
            Long id,
            LocalDate workDate,
            String label
    ) {}
}
