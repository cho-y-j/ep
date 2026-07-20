package com.skep.safetyboard.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P4a 안전 상황판 응답 DTO — 맵 마커 + 요약 스트립을 현장 1건 단위로 조립.
 * JSON 전역 SNAKE_CASE — 필드는 camelCase record.
 */
public class SafetyBoardDtos {

    /** 접근 가능 현장 목록 카드. */
    public record BoardSite(
            Long id, String name, String code, boolean hasGeo, long unresolvedAlerts) {}

    /** 현장 상세 보드 — 지도 재료 + 요약 + 공지 + P5-W0 워치 타일. */
    public record SiteBoard(
            Long siteId, String siteName, String code, String address,
            Double latitude, Double longitude, String polygonGeojson,
            Integer mapZoom, Integer geofenceRadiusM,
            List<WorkerMarker> workers,
            List<AlertMarker> alerts,
            Summary summary,
            List<AnnouncementSummary> announcements,
            List<WatchWorker> watchWorkers) {}

    /**
     * P5-W0 워커 워치 타일 — 상태등(state)·마지막 수신(secondsSinceSeen)·배터리·착용.
     * 회색(미착용/두절) 판정은 프론트에서 worn·secondsSinceSeen 로 파생.
     * P5-W1: hrSeries=최근 2h 심박(스파크라인), rest/work 대역=정상범위 밴드, baselineLearned=학습 여부(미학습 뱃지).
     * 지도 통합: bodyTemp(체온)·lat/lng(최근 위치, 워치 지도 마커)·bpVerdict(오늘 혈압 판정 OK|CAUTION|BLOCK, null=미측정).
     * 안전 지도: vehicleNo(조종 장비 차량번호)·vehicleStatus(장비 배치상태 ASSIGNED|AVAILABLE|BROKEN)·role(투입 역할)·
     * supplierName/supplierCompanyId(소속 공급사)·bpName/bpCompanyId(원청 BP) — work_plan_persons+장비+work_plan 조인.
     */
    public record WatchWorker(
            Long personId, String name, String state,
            LocalDateTime lastSeenAt, Long secondsSinceSeen,
            Integer battery, Boolean worn, Integer hr,
            java.math.BigDecimal bodyTemp,
            Double lat, Double lng,
            List<Integer> hrSeries,
            Integer restHrLow, Integer restHrHigh, Integer workHrLow, Integer workHrHigh,
            boolean baselineLearned,
            String healthRiskLevel,      // P5-W4 2겹: NORMAL|CAUTION|HIGH (HIGH=관제 뱃지).
            String bpVerdict,
            String vehicleNo, String vehicleStatus, String role,
            String supplierName, Long supplierCompanyId,
            String bpName, Long bpCompanyId) {}

    /** 오늘 출근 작업자 마커. checkedIn=체크인 중(미퇴근). */
    public record WorkerMarker(
            Long personId, String name, Double lat, Double lng,
            boolean checkedIn, LocalDateTime checkInAt) {}

    /** 미해결 경보 마커. unacked=확인 필요한데 미확인(펄스 강조 대상). */
    public record AlertMarker(
            Long id, String kind, String level, String severity, String message,
            String personName, Double lat, Double lng,
            LocalDateTime acknowledgedAt, LocalDateTime escalatedAt, LocalDateTime createdAt,
            boolean unacked) {}

    /** 요약 스트립 수치. */
    public record Summary(
            Weather weather,
            int deployed, int attended, int checkedIn,
            int unackedAlerts,
            int legalDone, int legalTarget,
            int operatorDone, int operatorTarget,
            int announcementRead, int announcementTotal) {}

    /** 체감온도·폭염단계·풍속. available=false면 KMA 미조회(키 없음/좌표 없음/실패). level=info|caution|warning|danger. */
    public record Weather(
            boolean available, Double feelsLike, String stage, String stageLabel, String level,
            Double windMps, boolean windStopActive) {}

    /** 현장 공지별 확인율 — [공지] 탭 목록. */
    public record AnnouncementSummary(
            Long id, String title, LocalDateTime createdAt, int recipientCount, int readCount) {}

    /** 공지 수신자 확인 상태 — 미확인자 명단 드릴다운. */
    public record RecipientStatus(Long personId, String name, LocalDateTime readAt) {}
}
