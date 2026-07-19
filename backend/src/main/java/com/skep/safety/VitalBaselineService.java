package com.skep.safety;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * P5-W1 개인 심박 대역 학습 + 자기보정.
 * - 대역 학습: ⓐ 워치 캘리브레이션 부트스트랩(1회) ⓑ raw readings 정상값 분위수 재학습(크론, drift 추종).
 * - 보정: 자가취소(오탐)→완화(+2%p, 상한 +20) / 관리자 실제사건→강화(-2%p, 하한 -10). 대역 재학습이 보정을 지우지 않음.
 * 판정·보정 math 는 순수 static (단위 테스트 대상), 인스턴스 메서드는 DB upsert 배선.
 */
@Service
@RequiredArgsConstructor
public class VitalBaselineService {

    /** 보정 스텝·캡(%p). +=완화(임계 상향, 오탐 억제), -=강화(임계 하향, 민감도↑). */
    static final BigDecimal STEP = new BigDecimal("2");
    static final BigDecimal CAP_RELAX = new BigDecimal("20");
    static final BigDecimal CAP_STRENGTHEN = new BigDecimal("-10");
    /** 대역 재학습 최소 표본(정상 readings). 미만이면 학습 보류(미학습 유지). */
    static final int MIN_LEARN_SAMPLES = 30;
    /** 자가취소(오탐) 인정 창(분) — 경보 생성 후 이 시간 내 본인 확인이어야 완화 피드백. */
    static final long FP_WINDOW_MIN = 15;
    /** 주기 재학습 조회 기간(일) — 최근 N일 정상 readings 로 대역 재산출(drift 추종). */
    static final int LOOKBACK_DAYS = 14;

    private static final Logger log = LoggerFactory.getLogger(VitalBaselineService.class);

    private final PersonVitalBaselineRepository repo;
    private final FieldSensorReadingRepository sensorRepo;

    // ── 순수 로직(단위 테스트 대상) ────────────────────────────────

    /** 정상 상태 HR 목록 → 개인 대역(p5/p50/p90). 표본 부족이면 null(미학습). */
    public static Band learnBand(List<Integer> normalHr) {
        List<Integer> vals = normalHr.stream().filter(h -> h != null && h > 0).sorted().toList();
        if (vals.size() < MIN_LEARN_SAMPLES) return null;
        int p50 = percentile(vals, 50);
        return new Band(percentile(vals, 5), p50, p50, percentile(vals, 90), vals.size());
    }

    static int percentile(List<Integer> sortedAsc, double p) {
        int idx = (int) Math.round((sortedAsc.size() - 1) * p / 100.0);
        return sortedAsc.get(Math.max(0, Math.min(sortedAsc.size() - 1, idx)));
    }

    /** 정상 상태(온디바이스 미발화 = NORMAL/미상) readings 의 유효 HR만 — 대역 학습 입력. */
    static List<Integer> normalHr(List<FieldSensorReading> readings) {
        return readings.stream()
                .filter(r -> r.getState() == null || "NORMAL".equalsIgnoreCase(r.getState()))
                .map(FieldSensorReading::getHr)
                .filter(h -> h != null && h > 0)
                .toList();
    }

    /** 오탐 누적 시 완화(임계 상향). 상한 +20%. */
    public static BigDecimal relax(BigDecimal adjustPct) {
        return adjustPct.add(STEP).min(CAP_RELAX);
    }

    /** 실제사건 시 강화(임계 하향). 하한 -10%. */
    public static BigDecimal strengthen(BigDecimal adjustPct) {
        return adjustPct.subtract(STEP).max(CAP_STRENGTHEN);
    }

    /** 자가취소(오탐) 판정 — 경보 생성 후 FP_WINDOW_MIN 내 본인 확인이면 완화 피드백 인정. */
    public static boolean isSelfCancel(LocalDateTime createdAt, LocalDateTime ackAt) {
        return createdAt != null && ackAt != null && !ackAt.isAfter(createdAt.plusMinutes(FP_WINDOW_MIN));
    }

    /** 보정 반영 실효 작업 상한 = work_hr_high × (1 + adjust/100). null 대역이면 null. */
    public static Integer effectiveWorkHrHigh(Integer workHrHigh, BigDecimal adjustPct) {
        if (workHrHigh == null) return null;
        BigDecimal factor = BigDecimal.ONE.add(adjustPct.divide(new BigDecimal("100")));
        return new BigDecimal(workHrHigh).multiply(factor).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    public record Band(int restHrLow, int restHrHigh, int workHrLow, int workHrHigh, int sampleCount) {}

    // ── DB 배선 ────────────────────────────────────────────────

    public Optional<PersonVitalBaseline> find(Long personId) {
        return repo.findById(personId);
    }

    /**
     * 워치 캘리브레이션(field_baselines) 으로 대역 부트스트랩 — 최초 1회(미학습일 때만).
     * 이후 대역은 서버 크론이 raw readings 로 재학습(더 정확) → 30분마다 오는 캘리브레이션이 덮어쓰지 않게 가드.
     */
    @Transactional
    public void seedFromCalibration(Long personId, FieldBaseline b) {
        if (b == null || b.getHrRestMean() == null) return;
        PersonVitalBaseline pv = repo.findById(personId).orElse(null);
        if (pv != null && pv.isLearned()) return;   // 이미 대역 있음 — 부트스트랩 스킵(크론 소유).

        double rest = b.getHrRestMean().doubleValue();
        double std = b.getHrRestStd() != null ? b.getHrRestStd().doubleValue() : 8.0;
        int restLow = (int) Math.max(40, Math.round(rest - 2 * std));
        int restHigh = (int) Math.round(rest);
        int workHigh = b.getAlertHrUpper() != null ? b.getAlertHrUpper().intValue()
                : b.getHrActiveMean() != null ? (int) Math.round(b.getHrActiveMean().doubleValue() + 2 * std)
                : (int) Math.round(rest + 50);
        Band band = new Band(restLow, restHigh, restHigh, Math.max(workHigh, restHigh + 5),
                b.getSamplesCount() != null ? b.getSamplesCount() : 0);
        applyBand(personId, band);
    }

    /** raw 정상 readings 로 대역 재학습(크론). 표본 부족이면 무변경. adjust/fp/tp 보존. */
    @Transactional
    public boolean relearnFromReadings(Long personId, List<Integer> normalHr) {
        Band band = learnBand(normalHr);
        if (band == null) return false;
        applyBand(personId, band);
        return true;
    }

    /** 최근 readings 있는 전 작업자 대역 재학습(크론/ADMIN 수동). @return 학습 성사 인원 수. */
    public int relearnAll() {
        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        List<Long> personIds = sensorRepo.findDistinctPersonIdsByRecordedAtAfter(since);
        int learned = 0;
        for (Long pid : personIds) {
            List<Integer> hr = normalHr(sensorRepo.findByPersonIdAndRecordedAtAfterOrderByRecordedAtDesc(pid, since));
            if (relearnFromReadings(pid, hr)) learned++;
        }
        if (!personIds.isEmpty()) log.info("VitalBaseline relearn: {}/{} learned", learned, personIds.size());
        return learned;
    }

    /** 대역만 갱신 — 보정 상태(adjust_pct·fp·tp)는 절대 초기화하지 않음(학습이 보정을 지우면 안 됨). */
    private void applyBand(Long personId, Band band) {
        PersonVitalBaseline pv = getOrCreate(personId);
        pv.setRestHrLow(band.restHrLow());
        pv.setRestHrHigh(band.restHrHigh());
        pv.setWorkHrLow(band.workHrLow());
        pv.setWorkHrHigh(band.workHrHigh());
        pv.setSampleCount(band.sampleCount());
        pv.setLearnedAt(LocalDateTime.now());
        repo.save(pv);
    }

    /** 오탐(작업자 자가취소) — 완화 + fp 카운트. 낙상·SOS·데드맨은 호출부에서 kind 로 제외됨. */
    @Transactional
    public void markFalsePositive(Long personId) {
        PersonVitalBaseline pv = getOrCreate(personId);
        pv.setAdjustPct(relax(pv.getAdjustPct()));
        pv.setFpCount(pv.getFpCount() + 1);
        repo.save(pv);
    }

    /** 실제사건(관리자 확인) — 강화 + tp 카운트. */
    @Transactional
    public void markRealEvent(Long personId) {
        PersonVitalBaseline pv = getOrCreate(personId);
        pv.setAdjustPct(strengthen(pv.getAdjustPct()));
        pv.setTpCount(pv.getTpCount() + 1);
        repo.save(pv);
    }

    private PersonVitalBaseline getOrCreate(Long personId) {
        return repo.findById(personId).orElseGet(() -> {
            PersonVitalBaseline pv = new PersonVitalBaseline();
            pv.setPersonId(personId);
            return pv;
        });
    }
}
