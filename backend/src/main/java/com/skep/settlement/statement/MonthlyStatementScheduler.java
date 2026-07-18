package com.skep.settlement.statement;

import com.skep.notification.NotificationRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.quotation.dispatch.DispatchedPersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 거래내역서 월마감 in-app 알림 — 매월 1일 09:00, 배차행 보유 공급사(+하위공급사)에게
 * "전월 거래내역서 준비됨"을 통지. 파일 생성/이메일 없음 — 기존 on-demand 다운로드(정산 &gt; 거래내역서) 안내만.
 *
 * 대상: 배차(장비/인원) 행의 distinct 명의 공급사 + 자기 귀속(sub_supplier) 공급사.
 * 스팸 방지: 같은 달 재실행 시 (회사, type) 이미 생성됐으면 skip — ExpiringDocumentScheduler 의 dedup 가드 준용.
 */
@Component
@RequiredArgsConstructor
public class MonthlyStatementScheduler {

    private final DispatchedEquipmentRepository dispatchedEquipments;
    private final DispatchedPersonRepository dispatchedPersons;
    private final NotificationService notifications;
    private final NotificationRepository notificationRepo;

    @Scheduled(cron = "0 0 9 1 * *")   // 매월 1일 09:00
    @Transactional
    public void remindMonthlyStatement() {
        // 배차행 보유 distinct 공급사(명의) + 자기 귀속(sub) 공급사 합집합.
        Set<Long> suppliers = new LinkedHashSet<>();
        suppliers.addAll(dispatchedEquipments.findDistinctSupplierCompanyIds());
        suppliers.addAll(dispatchedEquipments.findDistinctSubSupplierCompanyIds());
        suppliers.addAll(dispatchedPersons.findDistinctSupplierCompanyIds());
        suppliers.addAll(dispatchedPersons.findDistinctSubSupplierCompanyIds());
        if (suppliers.isEmpty()) return;

        LocalDate today = LocalDate.now();
        YearMonth prev = YearMonth.from(today.minusMonths(1));
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        String title = "거래내역서 준비 완료";
        String message = "전월(" + prev + ") 거래내역서가 준비되었습니다. 정산 > 거래내역서에서 다운로드하세요.";

        for (Long companyId : suppliers) {
            // 같은 달 (회사, type) 이미 생성됐으면 skip — 재실행/재기동 중복 방지.
            if (notificationRepo.existsByTargetCompanyIdAndTypeAndCreatedAtGreaterThanEqual(
                    companyId, NotificationType.MONTHLY_STATEMENT_READY, monthStart)) {
                continue;
            }
            notifications.sendToCompany(companyId, NotificationType.MONTHLY_STATEMENT_READY,
                    title, message, "SETTLEMENT_STATEMENT", null, null, "시스템 (월 마감)");
        }
    }
}
