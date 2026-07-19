package com.skep.health;

import com.skep.contract.RateType;
import com.skep.dailywork.DailyWorkLog;
import com.skep.dailywork.DailyWorkLogRepository;
import com.skep.field.FieldFcmService;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.FieldSafetyAlert;
import com.skep.safety.FieldSafetyAlertRepository;
import com.skep.safety.SafetyAlertBroadcaster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P5-W4 3겹 — 과로 크론 배선/가드 검증(스케줄러 규약 = mock 상호작용, WatchDeadmanSchedulerTest 방식).
 * 경계 판정은 OverworkEvaluatorTest(순수). 여기선 발화 시 alert(overwork·CAUTION)+FCM+관리자 통보 + 일 1회 가드.
 */
class OverworkSchedulerTest {

    private final DailyWorkLogRepository logs = mock(DailyWorkLogRepository.class);
    private final PersonRepository persons = mock(PersonRepository.class);
    private final FieldSafetyAlertRepository alerts = mock(FieldSafetyAlertRepository.class);
    private final FieldFcmService fcm = mock(FieldFcmService.class);
    private final SafetyAlertBroadcaster broadcaster = mock(SafetyAlertBroadcaster.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OverworkScheduler scheduler =
            new OverworkScheduler(logs, persons, alerts, fcm, broadcaster, notifications);

    private static final long PERSON = 10L, SUPPLIER = 2L, BP = 1L, SITE = 5L;

    private DailyWorkLog nightLog(LocalDate date) {
        DailyWorkLog l = DailyWorkLog.create(SUPPLIER, null);
        l.setPersonId(PERSON);
        l.setWorkDate(date);
        l.setRateType(RateType.DAILY);
        l.setSiteId(SITE);
        l.setBpCompanyId(BP);
        l.setOtNight(BigDecimal.valueOf(3));
        return l;
    }

    private Person person() {
        Person p = Person.builder().supplierId(SUPPLIER).name("과로테스트").build();
        p.updateFcmToken("tok-123");
        return p;
    }

    @Test
    void threeNightsFiresOverworkAlertAndNotifiesBothManagers() {
        LocalDate today = LocalDate.now();
        when(logs.findByWorkDateBetweenOrderByPersonIdAscWorkDateAsc(any(), any()))
                .thenReturn(List.of(nightLog(today.minusDays(2)), nightLog(today.minusDays(1)), nightLog(today)));
        when(alerts.existsByPersonIdAndKindAndCreatedAtAfter(eq(PERSON), eq("overwork"), any())).thenReturn(false);
        when(persons.findById(PERSON)).thenReturn(Optional.of(person()));

        scheduler.checkOverwork();

        verify(alerts).save(argThat(a ->
                "overwork".equals(a.getKind()) && "CAUTION".equals(a.getSeverity()) && PERSON == a.getPersonId()));
        verify(fcm).sendSafety(anyList(), eq("overwork"), any(), any(), eq("CAUTION"), any(), eq(true), any());
        // 관리자 통보 = BP(1) + 공급사(2) 각 1회.
        verify(notifications).sendToCompany(eq(BP), eq(NotificationType.OVERWORK_WARNING),
                any(), any(), eq("SITE"), eq(SITE), eq(SITE), any());
        verify(notifications).sendToCompany(eq(SUPPLIER), eq(NotificationType.OVERWORK_WARNING),
                any(), any(), eq("SITE"), eq(SITE), eq(SITE), any());
    }

    @Test
    void perDayGuardPreventsSecondFire() {
        LocalDate today = LocalDate.now();
        when(logs.findByWorkDateBetweenOrderByPersonIdAscWorkDateAsc(any(), any()))
                .thenReturn(List.of(nightLog(today.minusDays(2)), nightLog(today.minusDays(1)), nightLog(today)));
        when(alerts.existsByPersonIdAndKindAndCreatedAtAfter(eq(PERSON), eq("overwork"), any())).thenReturn(true);  // 이미 오늘 발화.

        scheduler.checkOverwork();

        verify(alerts, never()).save(any(FieldSafetyAlert.class));
        verify(fcm, never()).sendSafety(anyList(), any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(notifications, never()).sendToCompany(anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void twoNightsDoesNotFire() {
        LocalDate today = LocalDate.now();
        when(logs.findByWorkDateBetweenOrderByPersonIdAscWorkDateAsc(any(), any()))
                .thenReturn(List.of(nightLog(today.minusDays(1)), nightLog(today)));   // 야간 2일 + 6h < 60h.

        scheduler.checkOverwork();

        verify(alerts, never()).save(any(FieldSafetyAlert.class));
    }
}
