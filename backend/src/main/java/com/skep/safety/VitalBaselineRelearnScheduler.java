package com.skep.safety;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P5-W1 개인 심박 대역 주기 재학습 — 주 1회(일요일 03:00) 최근 정상 readings 로 대역 재산출(drift 추종).
 * 오케스트레이션은 VitalBaselineService.relearnAll (ADMIN 수동 트리거와 단일 소스). 보정 상태(adjust/fp/tp)는 보존.
 */
@Component
@RequiredArgsConstructor
public class VitalBaselineRelearnScheduler {

    private final VitalBaselineService baselineService;

    @Scheduled(cron = "0 0 3 * * SUN")
    public void weekly() {
        baselineService.relearnAll();
    }
}
