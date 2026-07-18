package com.skep.safety;

import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.weather.HeatStage;
import com.skep.weather.KmaWeatherClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * P5-W0 워치 원격 정책 — "침묵이 정상"의 3색 상태머신을 서버가 현장 맥락으로 가변.
 * W0 최소 규칙: 폭염경보(체감 33℃↑)·강풍 작업중지 시 YELLOW(전송·듀티 상향), 그 외 GREEN.
 * (고위험군 태그 연동은 W1/W4 몫 — 여기선 기상 맥락만.)
 */
@Service
@RequiredArgsConstructor
public class WatchPolicyService {

    /** GREEN: 심박 저듀티 60초·10분 묶음 전송. YELLOW: 30초·5분 묶음. */
    private static final int GREEN_SEND_SEC = 600;
    private static final int GREEN_HR_DUTY_SEC = 60;
    private static final int YELLOW_SEND_SEC = 300;
    private static final int YELLOW_HR_DUTY_SEC = 30;

    private final SiteRepository sites;
    private final SiteSafetySettingsRepository safetySettings;
    private final SiteWindStateRepository windStates;
    private final KmaWeatherClient weather;

    /** 워치가 적용할 전송 정책. state = GREEN|YELLOW (RED 는 워치 온디바이스 경보가 자체 판정). */
    public record WatchPolicy(String state, int sendIntervalSec, int hrDutySec) {
        static WatchPolicy green() { return new WatchPolicy("GREEN", GREEN_SEND_SEC, GREEN_HR_DUTY_SEC); }
        static WatchPolicy yellow() { return new WatchPolicy("YELLOW", YELLOW_SEND_SEC, YELLOW_HR_DUTY_SEC); }
    }

    /**
     * 순수 판정(단위 테스트용) — 기상 맥락에서 YELLOW 여부.
     * 폭염경보(WARNING/DANGER/EXTREME) 또는 강풍 작업중지 활성 → YELLOW.
     */
    public static boolean isElevated(HeatStage stage, boolean windStopActive) {
        if (windStopActive) return true;
        return stage == HeatStage.WARNING || stage == HeatStage.DANGER || stage == HeatStage.EXTREME;
    }

    /** 현장 맥락으로 정책 산출. 현장/좌표/기상 미상이면 GREEN(기본 저전력). */
    public WatchPolicy policyForSite(Long siteId) {
        if (siteId == null) return WatchPolicy.green();
        boolean windStop = windStates.findBySiteId(siteId).map(SiteWindState::isActive).orElse(false);
        HeatStage stage = HeatStage.NONE;
        Site site = sites.findById(siteId).orElse(null);
        if (site != null && site.getLatitude() != null && site.getLongitude() != null) {
            SafetyThresholds thresholds = SafetyThresholds.from(safetySettings.findBySiteId(siteId).orElse(null));
            stage = weather.fetch(site.getLatitude(), site.getLongitude())
                    .map(w -> thresholds.stageOf(w.feelsLike()))
                    .orElse(HeatStage.NONE);
        }
        return isElevated(stage, windStop) ? WatchPolicy.yellow() : WatchPolicy.green();
    }

    /**
     * 센서 상태 문자열 → 관제 상태등(GREEN|YELLOW|RED). 워치가 보내는 표기가
     * WorkerState.name(NORMAL/EMERGENCY…) 또는 normal/caution/danger 둘 다일 수 있어 폭넓게 매핑.
     */
    public static String colorOf(String sensorState) {
        if (sensorState == null || sensorState.isBlank()) return "GREEN";
        String u = sensorState.toUpperCase();
        if (u.contains("DANGER") || u.contains("EMERGENCY") || u.contains("FALL") || u.equals("RED")) return "RED";
        if (u.contains("CAUTION") || u.contains("ANOMALY") || u.contains("ACK") || u.contains("WAIT")
                || u.equals("YELLOW")) return "YELLOW";
        return "GREEN";
    }
}
