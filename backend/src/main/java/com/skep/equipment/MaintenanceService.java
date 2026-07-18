package com.skep.equipment;

import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.DailyWorkLogRepository;
import com.skep.safety.SiteSafetySettings;
import com.skep.safety.SiteSafetySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * S4'(P3a): 장비 누적 가동시간 집계 + 정비 주기 도달 판정.
 * 누적 가동시간 = 일일 확인서(start/end 있으면 그 시간, 없으면 8h 기본) 합산(조회 시 집계, 배치).
 * 정비 주기 = 장비 현재 배치 현장의 안전설정(maintenance_interval_hours, NULL=비활성).
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final DailyWorkLogRepository dailyWorkLogRepo;
    private final SiteSafetySettingsRepository safetySettingsRepo;

    /** 정비 뷰 — 장비 목록/상세 응답 + 스케줄러 공통. intervalHours=null=비활성. */
    public record MaintenanceView(int cumulativeHours, Integer intervalHours, boolean due) {}

    /** 일일 확인서 1건의 가동시간 — start/end 있으면 그 차이(철야=자정 넘김 보정), 없으면 8h 기본. */
    public static double logHours(DailyWorkLog l) {
        LocalTime st = l.getStartTime();
        LocalTime en = l.getEndTime();
        if (st != null && en != null) {
            long min = Duration.between(st, en).toMinutes();
            if (min < 0) min += 24 * 60; // 종료가 시작보다 이르면 자정 넘긴 철야로 간주.
            return min / 60.0;
        }
        return 8.0;
    }

    /** 정비 도래 판정(순수) — 주기 설정됨 && (누적 - 최근 알림 기준치) >= 주기. */
    public static boolean isDue(int cumulativeHours, int alertBaseHours, Integer intervalHours) {
        return intervalHours != null && (cumulativeHours - alertBaseHours) >= intervalHours;
    }

    /** 장비 집합의 정비 뷰 일괄(N+1 회피) — 목록/상세 응답용. */
    @Transactional(readOnly = true)
    public Map<Long, MaintenanceView> viewByEquipment(List<Equipment> equipment) {
        if (equipment.isEmpty()) return Map.of();
        List<Long> ids = equipment.stream().map(Equipment::getId).toList();

        Map<Long, Double> cumulative = new HashMap<>();
        for (DailyWorkLog l : dailyWorkLogRepo.findByEquipmentIdIn(ids)) {
            if (l.getEquipmentId() == null) continue;
            cumulative.merge(l.getEquipmentId(), logHours(l), Double::sum);
        }

        Map<Long, Integer> intervalBySite = intervalBySite(
                equipment.stream().map(Equipment::getCurrentSiteId).filter(Objects::nonNull).distinct().toList());

        Map<Long, MaintenanceView> out = new HashMap<>();
        for (Equipment e : equipment) {
            int cum = (int) Math.round(cumulative.getOrDefault(e.getId(), 0.0));
            Integer interval = e.getCurrentSiteId() != null ? intervalBySite.get(e.getCurrentSiteId()) : null;
            out.put(e.getId(), new MaintenanceView(cum, interval, isDue(cum, e.getMaintenanceAlertHours(), interval)));
        }
        return out;
    }

    /** 여러 현장의 정비 주기(설정 있고 주기 지정된 것만). */
    public Map<Long, Integer> intervalBySite(List<Long> siteIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (siteIds.isEmpty()) return map;
        for (SiteSafetySettings s : safetySettingsRepo.findBySiteIdIn(siteIds)) {
            if (s.getMaintenanceIntervalHours() != null) map.put(s.getSiteId(), s.getMaintenanceIntervalHours());
        }
        return map;
    }

    /** 단일 장비 누적 가동시간 — 스케줄러 알림 후 기준치 갱신에 사용. */
    public int cumulativeHours(Long equipmentId) {
        double sum = 0;
        for (DailyWorkLog l : dailyWorkLogRepo.findByEquipmentIdIn(List.of(equipmentId))) {
            sum += logHours(l);
        }
        return (int) Math.round(sum);
    }
}
