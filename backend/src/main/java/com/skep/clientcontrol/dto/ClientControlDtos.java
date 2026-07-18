package com.skep.clientcontrol.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 원청(client_org) 통합 관제 허브 — 읽기전용 집계 응답 모음(§1.1). JSON 은 전역 snake_case. */
public final class ClientControlDtos {

    private ClientControlDtos() {}

    /** GET /api/client/sites — 내 원청 현장 목록 카드. */
    public record ClientSiteSummary(
            Long siteId,
            String name,
            String code,
            String bpCompanyName,
            String status,
            int participantCount,
            int deployedPersonCount,
            int currentlyCheckedIn,
            long unresolvedAlertCount
    ) {}

    public record SupplierItem(Long companyId, String name, String type) {}

    /** 투입 장비 상태별 집계. */
    public record EquipmentStatusCount(int total, long assigned, long available, long broken) {}

    /** 오늘 출근 현황. */
    public record AttendanceSummary(int deployedPersonCount, int attendedToday, int currentlyCheckedIn) {}

    /** 혼잡도 v1 — 시간(0~23)별 "체크인 상태였던 인원 수". 구역 드릴다운은 range 밖(현장 단위). */
    public record Congestion(int[] todayByHour, int[] weekAvgByHour) {}

    /** 일일점검 2트랙 — 조종원 일일점검(equipmentTarget/doneToday) + S2′ 법정점검(legalTarget/legalDone). */
    public record DailyInspection(int equipmentTarget, int doneToday, int legalTarget, int legalDone) {}

    public record AlertItem(Long id, String kind, String level, String message,
                            String personName, LocalDateTime createdAt,
                            String severity, LocalDateTime acknowledgedAt, LocalDateTime escalatedAt) {}

    public record ExpiringItem(String ownerType, String ownerLabel, LocalDate expiryDate, long dDay) {}

    /** GET /api/client/sites/{id}/overview — 현장 통합 관제 상세. */
    public record ClientSiteOverview(
            Long siteId,
            String name,
            String code,
            String address,
            String bpCompanyName,
            String clientOrgName,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            List<SupplierItem> suppliers,
            EquipmentStatusCount equipment,
            AttendanceSummary attendance,
            Congestion congestion,
            DailyInspection dailyInspection,
            long unresolvedAlertCount,
            List<AlertItem> recentAlerts,
            long expiringD30Count,
            List<ExpiringItem> expiringDocs
    ) {}
}
