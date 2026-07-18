package com.skep.equipment;

import com.skep.equipment.MaintenanceService.MaintenanceView;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.safety.SiteSafetySettings;
import com.skep.safety.SiteSafetySettingsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * S4'(P3a): 가동시간 정비 알림 — 매일 08:40. 정비 주기 설정 현장에 배치된 장비의 누적 가동시간이
 * (기준 + 주기) 도달 시 장비 공급사에 인앱 알림 1회 발송 후 기준치 갱신(다음 주기까지 재알림 없음).
 * 날짜 기반 oil_change_due(V70) 알림과 병존.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final SiteSafetySettingsRepository safetySettingsRepo;
    private final EquipmentRepository equipmentRepo;
    private final MaintenanceService maintenanceService;
    private final NotificationService notifications;

    @Scheduled(cron = "0 40 8 * * *")
    @Transactional
    public void remindMaintenanceDue() {
        List<SiteSafetySettings> active = safetySettingsRepo.findByMaintenanceIntervalHoursIsNotNull();
        if (active.isEmpty()) return;
        List<Long> siteIds = active.stream().map(SiteSafetySettings::getSiteId).toList();

        List<Equipment> equipment = equipmentRepo.findByCurrentSiteIdIn(siteIds);
        if (equipment.isEmpty()) return;
        Map<Long, MaintenanceView> views = maintenanceService.viewByEquipment(equipment);

        int sent = 0;
        for (Equipment e : equipment) {
            MaintenanceView v = views.get(e.getId());
            if (v == null || !v.due() || e.getSupplierId() == null) continue;
            String name = e.getVehicleNo() != null ? e.getVehicleNo()
                    : (e.getModel() != null ? e.getModel() : "장비#" + e.getId());
            String msg = name + " 정비 도래 — 누적 가동 " + v.cumulativeHours() + "h (주기 " + v.intervalHours()
                    + "h). 점검·정비를 진행하세요.";
            notifications.sendToCompany(e.getSupplierId(), NotificationType.EQUIPMENT_MAINTENANCE_DUE,
                    "정비 도래", msg, "EQUIPMENT", e.getId(), e.getCurrentSiteId());
            e.markMaintenanceAlerted(v.cumulativeHours());
            equipmentRepo.save(e);
            sent++;
        }
        if (sent > 0) log.info("MaintenanceScheduler: {} maintenance-due alerts sent", sent);
    }
}
