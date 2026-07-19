package com.skep.safety;

import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.weather.HeatStage;
import com.skep.weather.KmaWeatherClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

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
    /** 폭염단계 캐시 TTL — KMA 초단기실황은 시간단위라 5분 캐시로 무손실 + 센서 경로 부하/트랜잭션 점유 방지. */
    private static final long HEAT_CACHE_MS = 5 * 60 * 1000L;

    private final SiteRepository sites;
    private final SiteSafetySettingsRepository safetySettings;
    private final SiteWindStateRepository windStates;
    private final KmaWeatherClient weather;

    /** siteId → 최근 체감온도(null=미조회) 캐시. */
    private final ConcurrentHashMap<Long, CachedFeels> feelsCache = new ConcurrentHashMap<>();
    private record CachedFeels(Double feelsLike, long at) {}

    /**
     * 워치가 적용할 전송 정책. state = GREEN|YELLOW (RED 는 워치 온디바이스 경보가 자체 판정).
     * hrLow/hrHigh = P5-W1 개인 심박 대역(보정 반영) — 워치 온디바이스 1차 판정 임계용(미학습이면 null).
     */
    public record WatchPolicy(String state, int sendIntervalSec, int hrDutySec, Integer hrLow, Integer hrHigh) {
        static WatchPolicy green() { return new WatchPolicy("GREEN", GREEN_SEND_SEC, GREEN_HR_DUTY_SEC, null, null); }
        static WatchPolicy yellow() { return new WatchPolicy("YELLOW", YELLOW_SEND_SEC, YELLOW_HR_DUTY_SEC, null, null); }
        /** 개인 대역 부착(워치 1차 판정용). */
        public WatchPolicy withBand(Integer low, Integer high) {
            return new WatchPolicy(state, sendIntervalSec, hrDutySec, low, high);
        }
    }

    /**
     * 순수 판정(단위 테스트용) — 기상 맥락에서 YELLOW 여부.
     * 폭염경보(WARNING/DANGER/EXTREME) 또는 강풍 작업중지 활성 → YELLOW.
     */
    public static boolean isElevated(HeatStage stage, boolean windStopActive) {
        if (windStopActive) return true;
        return stage == HeatStage.WARNING || stage == HeatStage.DANGER || stage == HeatStage.EXTREME;
    }

    /** P5-W4 2겹 — 고위험군(HIGH) 결합: 개인 리스크가 있으면 기상 맥락 무관 YELLOW. */
    public static boolean isElevated(HeatStage stage, boolean windStopActive, boolean highRisk) {
        return highRisk || isElevated(stage, windStopActive);
    }

    /** 현장 맥락으로 정책 산출. 현장/좌표/기상 미상이면 GREEN(기본 저전력). */
    public WatchPolicy policyForSite(Long siteId) {
        return policyForSite(siteId, false);
    }

    /** P5-W4 2겹 — 개인 고위험군(HIGH) 결합 정책. highRisk 면 현장/기상 무관 YELLOW(전송·듀티 상향). */
    public WatchPolicy policyForSite(Long siteId, boolean highRisk) {
        if (siteId == null) return highRisk ? WatchPolicy.yellow() : WatchPolicy.green();
        boolean windStop = windStates.findBySiteId(siteId).map(SiteWindState::isActive).orElse(false);
        return isElevated(heatStageForSite(siteId), windStop, highRisk) ? WatchPolicy.yellow() : WatchPolicy.green();
    }

    /** P5-W1 열스트레스 판정용 — 폭염단계 상향(WARNING↑) 여부(풍속 제외, 열 전용). isElevated 재사용. */
    public boolean isHeatElevatedForSite(Long siteId) {
        return isElevated(heatStageForSite(siteId), false);
    }

    /** 현장 폭염단계(5분 캐시). 좌표/키/데이터 미상이면 NONE. */
    private HeatStage heatStageForSite(Long siteId) {
        if (siteId == null) return HeatStage.NONE;
        Double feels = cachedFeelsLike(siteId);
        if (feels == null) return HeatStage.NONE;
        SafetyThresholds thresholds = SafetyThresholds.from(safetySettings.findBySiteId(siteId).orElse(null));
        return thresholds.stageOf(feels);
    }

    private Double cachedFeelsLike(Long siteId) {
        long now = System.currentTimeMillis();
        CachedFeels c = feelsCache.get(siteId);
        if (c != null && now - c.at() < HEAT_CACHE_MS) return c.feelsLike();
        Site site = sites.findById(siteId).orElse(null);
        Double feels = (site != null && site.getLatitude() != null && site.getLongitude() != null)
                ? weather.fetch(site.getLatitude(), site.getLongitude())
                    .map(KmaWeatherClient.SiteWeather::feelsLike).orElse(null)
                : null;
        feelsCache.put(siteId, new CachedFeels(feels, now));
        return feels;
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
