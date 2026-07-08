package com.skep.equipment;

import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** 차량 정기검사/오일교체/등록 만료 임박 알림 — 매일 08:30, 14/7/3/1/0일 전 공급사에 통지(스팸 방지: 이산 시점만). */
@Component
@RequiredArgsConstructor
public class EquipmentDueScheduler {

    private static final Set<Integer> WINDOWS = Set.of(14, 7, 3, 1, 0);

    private final EquipmentRepository equipmentRepo;
    private final NotificationService notifications;

    @Scheduled(cron = "0 30 8 * * *")
    @Transactional
    public void remindDue() {
        LocalDate today = LocalDate.now();
        for (Equipment e : equipmentRepo.findWithAnyDueDate()) {
            notifyIfDue(e, e.getInspectionDueDate(), "정기검사", today);
            notifyIfDue(e, e.getOilChangeDueDate(), "오일교체", today);
            notifyIfDue(e, e.getRegistrationExpiry(), "차량등록 만료", today);
        }
    }

    private void notifyIfDue(Equipment e, LocalDate due, String label, LocalDate today) {
        if (due == null || e.getSupplierId() == null) return;
        long days = ChronoUnit.DAYS.between(today, due);
        if (days < 0 || !WINDOWS.contains((int) days)) return;
        String name = e.getVehicleNo() != null ? e.getVehicleNo()
                : (e.getModel() != null ? e.getModel() : "장비#" + e.getId());
        String msg = days == 0 ? name + " " + label + " 오늘" : name + " " + label + " " + days + "일 남음";
        notifications.sendToCompany(e.getSupplierId(), NotificationType.EQUIPMENT_DUE,
                label + " 임박", msg, "EQUIPMENT", e.getId(), null);
    }
}
