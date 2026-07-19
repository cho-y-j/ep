package com.skep.safety;

import com.skep.safety.WindStopScheduler.Transition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P3a S1 — 강풍 작업중지 상태 전이(초과 진입 1회·해제 1회) 순수 판정.
 */
class WindStopSchedulerTest {

    @Test
    void windUnknownIsNoTransition() {
        assertEquals(Transition.NONE, WindStopScheduler.decide(null, 10, false));
        assertEquals(Transition.NONE, WindStopScheduler.decide(null, 10, true));
    }

    @Test
    void crossingUpEntersOnce() {
        assertEquals(Transition.ENTER, WindStopScheduler.decide(12.0, 10, false)); // 초과 진입
        assertEquals(Transition.NONE, WindStopScheduler.decide(12.0, 10, true));   // 이미 활성 → 재발송 없음
    }

    @Test
    void atThresholdEnters() {
        assertEquals(Transition.ENTER, WindStopScheduler.decide(10.0, 10, false)); // >= 임계
    }

    @Test
    void droppingBelowClearsOnce() {
        assertEquals(Transition.CLEAR, WindStopScheduler.decide(8.0, 10, true));  // 해제
        assertEquals(Transition.NONE, WindStopScheduler.decide(8.0, 10, false));  // 이미 비활성
    }

    @Test
    void stricterSiteThresholdTriggersEarlier() {
        // 현장 임계를 7m/s 로 강화 → 8m/s 에서 진입.
        assertEquals(Transition.ENTER, WindStopScheduler.decide(8.0, 7, false));
        assertEquals(Transition.NONE, WindStopScheduler.decide(8.0, 10, false));
    }
}
