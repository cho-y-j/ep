package com.skep.safety;

import com.skep.workplan.WorkPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P5-W0 데드맨 — "무수신 자체가 신호" 순수 판정(마지막 수신 30분 초과 = 두절).
 */
class WatchDeadmanSchedulerTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 7, 19, 12, 0);
    private final long after = WatchDeadmanScheduler.SILENT_AFTER_MIN;

    @Test
    void neverSeenIsSilent() {
        assertTrue(WatchDeadmanScheduler.isSilent(null, now, after));
    }

    @Test
    void recentIsNotSilent() {
        assertFalse(WatchDeadmanScheduler.isSilent(now.minusMinutes(10), now, after));
        assertFalse(WatchDeadmanScheduler.isSilent(now.minusMinutes(29), now, after));
    }

    @Test
    void olderThanThresholdIsSilent() {
        assertTrue(WatchDeadmanScheduler.isSilent(now.minusMinutes(31), now, after));
        assertTrue(WatchDeadmanScheduler.isSilent(now.minusMinutes(120), now, after));
    }

    @Test
    void exactlyAtThresholdIsNotYetSilent() {
        // 정확히 30분 전 = 경계(아직 두절 아님, isBefore(now-30) 미충족).
        assertFalse(WatchDeadmanScheduler.isSilent(now.minusMinutes(30), now, after));
    }

    @Test
    void deletedWorkPlanYieldsNullId() {
        // D1: 세션이 참조하는 계획서가 삭제됨(findById empty → null) → alert work_plan_id 는 null
        //     (FK 위반으로 분 배치가 롤백되던 결함 방지). 존재하면 그 id 그대로.
        assertNull(WatchDeadmanScheduler.resolveAlertWorkPlanId(null));
        WorkPlan wp = mock(WorkPlan.class);
        when(wp.getId()).thenReturn(42L);
        assertEquals(42L, WatchDeadmanScheduler.resolveAlertWorkPlanId(wp));
    }
}
