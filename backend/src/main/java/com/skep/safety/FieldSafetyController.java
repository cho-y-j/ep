package com.skep.safety;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.common.ApiException;
import com.skep.field.FieldTokenAuth;
import com.skep.field.FieldTokenRateLimiter;
import com.skep.person.Person;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 워치/폰이 호출하는 안전알림 엔드포인트. X-Field-Token (attendance_code) 인증. */
@RestController
@RequestMapping("/api/field-auth")
@RequiredArgsConstructor
public class FieldSafetyController {

    private final FieldTokenAuth fieldAuth;
    private final AttendanceSessionRepository attRepo;
    private final WorkPlanRepository wpRepo;
    private final FieldSafetyAlertRepository alertRepo;
    private final FieldSensorReadingRepository sensorRepo;
    private final FieldBaselineRepository baselineRepo;
    private final SafetyAlertBroadcaster broadcaster;
    private final FieldTokenRateLimiter rateLimiter;
    private final WorkerWatchStateRepository watchStateRepo;
    private final WatchPolicyService watchPolicyService;
    private final VitalAnomalyService vitalAnomalyService;
    private final VitalBaselineService vitalBaselineService;
    private final EmergencyResponseService emergencyResponseService;
    private final SafetyAlertResponseRepository responseRepo;

    /** P5-W1 2차 판정 평가창(분) — 추세·연속 판정용 최근 readings. */
    private static final long EVAL_WINDOW_MIN = 20;

    /**
     * 센서 데이터 수신 — raw 저장(그래프용) + 워치 상태 upsert(P5-W0 데드맨·관제 타일).
     * P5-W0: 배치 records[](폰이 10~5분 묶음 중계) 또는 단건(하위호환·워치 직접 폴백) 둘 다 수용.
     */
    @PostMapping("/sensor")
    @Transactional
    public Map<String, Object> sensor(@RequestHeader("X-Field-Token") String token,
                                      @RequestBody SensorRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        var ctx = openContext(p);

        List<SensorRecord> records = (req.records != null && !req.records.isEmpty())
                ? req.records : List.of(SensorRecord.of(req));
        FieldSensorReading last = null;
        for (SensorRecord rec : records) {
            FieldSensorReading r = new FieldSensorReading();
            r.setPersonId(p.getId());
            r.setWorkPlanId(ctx.workPlanId);
            r.setSiteId(ctx.siteId);
            r.setHr(rec.hr);
            r.setSpo2(rec.spo2);
            r.setBodyTemp(rec.bodyTemp);
            r.setStress(rec.stress);
            r.setState(rec.state);
            r.setLat(rec.lat);
            r.setLng(rec.lng);
            sensorRepo.save(r);
            last = r;
        }

        upsertWatchState(p, ctx, req, last);   // 데드맨 last_seen 갱신 + 수신 재개 시 자동 해소.

        // P5-W1 서버 2차 판정 — 개인 베이스라인 대비 맥락 평가(CAUTION만). 판정 실패가 센서 저장을 롤백하면 안 됨.
        try {
            List<FieldSensorReading> window = sensorRepo.findByPersonIdAndRecordedAtAfterOrderByRecordedAtAsc(
                    p.getId(), LocalDateTime.now().minusMinutes(EVAL_WINDOW_MIN));
            vitalAnomalyService.evaluate(p, ctx.siteId, ctx.bpCompanyId, window);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(FieldSafetyController.class)
                    .warn("vital 2차 판정 실패 person={}: {}", p.getId(), e.getMessage());
        }
        return Map.of("ok", true, "id", last != null ? last.getId() : null);
    }

    /**
     * P5-W0 워치 원격 정책 — 현장 폭염/강풍 맥락으로 전송주기·심박 듀티 가변(폰이 워치로 전달).
     * P5-W1: 개인 심박 대역(보정 반영)을 실어 워치 온디바이스 1차 판정 임계로 사용(미학습이면 null).
     */
    @GetMapping("/watch-policy")
    public WatchPolicyService.WatchPolicy watchPolicy(@RequestHeader("X-Field-Token") String token,
                                                      HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        // P5-W4 2겹: 고위험군(HIGH)이면 현장/기상 무관 YELLOW 상향.
        boolean highRisk = p.getHealthRiskLevel() == com.skep.person.HealthRiskLevel.HIGH;
        WatchPolicyService.WatchPolicy policy = watchPolicyService.policyForSite(openContext(p).siteId, highRisk);
        PersonVitalBaseline pv = vitalBaselineService.find(p.getId()).orElse(null);
        if (pv != null && pv.isLearned()) {
            Integer high = VitalBaselineService.effectiveWorkHrHigh(pv.getWorkHrHigh(), pv.getAdjustPct());
            policy = policy.withBand(pv.getRestHrLow(), high);
        }
        return policy;
    }

    /**
     * 워커 워치 상태 upsert(1인 1행). battery/worn 은 보고됐을 때만 갱신(직접 폴백 경로 미포함 보존).
     * deadman_alert_id 가 있으면 수신 재개로 보고 열린 데드맨 경보를 자동 resolve.
     */
    private void upsertWatchState(Person p, OpenContext ctx, SensorRequest req, FieldSensorReading last) {
        WorkerWatchState ws = watchStateRepo.findById(p.getId()).orElseGet(() -> {
            WorkerWatchState nw = new WorkerWatchState();
            nw.setPersonId(p.getId());
            return nw;
        });
        ws.setLastSeenAt(LocalDateTime.now());
        if (req.battery != null) ws.setBattery(req.battery);
        if (req.worn != null) ws.setWorn(req.worn);
        if (ctx.siteId != null) ws.setSiteId(ctx.siteId);
        if (ctx.bpCompanyId != null) ws.setBpCompanyId(ctx.bpCompanyId);
        if (last != null) {
            ws.setHr(last.getHr());
            ws.setSpo2(last.getSpo2());
            ws.setBodyTemp(last.getBodyTemp());
            ws.setState(WatchPolicyService.colorOf(last.getState()));
            // 위치 보고가 있을 때만 갱신(폰 GPS 없는 워치 직접 폴백은 이전 위치 보존).
            if (last.getLat() != null && last.getLng() != null) {
                ws.setLat(last.getLat());
                ws.setLng(last.getLng());
            }
        }
        if (ws.getDeadmanAlertId() != null) {
            FieldSafetyAlert open = alertRepo.findById(ws.getDeadmanAlertId()).orElse(null);
            if (open != null && !open.isResolved()) {
                open.setResolved(true);
                open.setResolvedAt(LocalDateTime.now());
                alertRepo.save(open);
                broadcaster.publishResolved(open);
            }
            ws.setDeadmanAlertId(null);
        }
        watchStateRepo.save(ws);
    }

    /** 긴급 알림 — 워치 6단계 상태머신의 EMERGENCY/FALL_DETECTED 등. */
    @PostMapping("/emergency")
    @Transactional
    public Map<String, Object> emergency(@RequestHeader("X-Field-Token") String token,
                                         @RequestBody EmergencyRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        var ctx = openContext(p);
        FieldSafetyAlert a = new FieldSafetyAlert();
        a.setPersonId(p.getId());
        a.setWorkPlanId(ctx.workPlanId);
        a.setSiteId(ctx.siteId);
        a.setBpCompanyId(ctx.bpCompanyId);
        a.setKind(req.kind != null ? req.kind : "emergency");
        a.setLevel(req.level != null ? req.level : "danger");
        a.setSeverity(SafetySeverity.EMERGENCY.name());   // S5': 워치 응급/낙상 = 긴급(관제 등급 표시용).
        a.setMessage(req.message);
        a.setHr(req.hr);
        a.setSpo2(req.spo2);
        a.setBodyTemp(req.bodyTemp);
        a.setStress(req.stress);
        a.setLat(req.lat);
        a.setLng(req.lng);
        alertRepo.save(a);
        broadcaster.publishCreated(a, p);
        emergencyResponseService.onEmergencyAlert(a, p);   // P5-W2 대응체인: 파인드미 + 근접 동료 3인.
        return Map.of("ok", true, "alert_id", a.getId());
    }

    /**
     * P5-W2 근접 동료 응답([제가 갑니다]) — GOING 저장(동료 1인 1응답, 멱등)·first_response_at 최초 1회·
     * 관제 WS 실시간 갱신. 응답자는 어느 현장 작업자든 가능(발신자 본인 아님) — alertId 로 특정.
     */
    @PostMapping("/safety-alerts/{alertId}/respond")
    @Transactional
    public Map<String, Object> respond(@RequestHeader("X-Field-Token") String token,
                                       @org.springframework.web.bind.annotation.PathVariable Long alertId,
                                       @RequestBody RespondRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        FieldSafetyAlert a = alertRepo.findById(alertId)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "알림을 찾을 수 없습니다"));
        String response = (req.response == null || req.response.isBlank()) ? "GOING" : req.response.trim();

        // 멱등 — 이미 응답한 동료면 중복 저장 안 함.
        SafetyAlertResponse r = responseRepo.findByAlertIdAndPersonId(alertId, p.getId()).orElse(null);
        if (r == null) {
            r = new SafetyAlertResponse();
            r.setAlertId(alertId);
            r.setPersonId(p.getId());
            r.setResponse(response);
            responseRepo.save(r);
        }
        if (a.getFirstResponseAt() == null) {   // 최초 응답만 t2 기록.
            a.setFirstResponseAt(LocalDateTime.now());
            alertRepo.save(a);
        }
        int count = responseRepo.findByAlertIdOrderByCreatedAtAsc(alertId).size();
        broadcaster.publishResponded(a, p.getName(), count);
        return Map.of("ok", true, "responder_count", count, "first_response_at", a.getFirstResponseAt());
    }

    /**
     * P5-W3 제3자 폰 BLE 대리중계 수신 — 피재자 활성 EMERGENCY 경보 위치 보강, 없으면 신규 생성 + 대응체인.
     * 인증 토큰 = 중계자(누구든). victim_person_id 로 피재자 특정. 서버가 victim당 5분 dedupe.
     */
    @PostMapping("/sos-relay")
    @Transactional
    public Map<String, Object> sosRelay(@RequestHeader("X-Field-Token") String token,
                                        @RequestBody SosRelayRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        fieldAuth.authenticate(token);   // 중계자 인증(신원 기록은 불필요 — 익명 중계).
        if (req.victimPersonId == null) {
            throw ApiException.badRequest("NO_VICTIM", "피재자 식별자가 없습니다");
        }
        return emergencyResponseService.receiveRelay(req.victimPersonId, req.relayLat, req.relayLng);
    }

    /** 베이스라인 동기화 (워치 → 서버). 30분 주기. */
    @PostMapping("/baseline/sync")
    @Transactional
    public Map<String, Object> baselineSync(@RequestHeader("X-Field-Token") String token,
                                            @RequestBody BaselineRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        // 무징후 실패 가드 — 안정시 심박(seedFromCalibration 의 부트스트랩 필수값)이 없으면 관대한 무시 대신 명시 오류.
        if (req.hrRestMean == null) {
            throw ApiException.badRequest("BAD_BASELINE", "베이스라인 필수 값(안정시 심박)이 없습니다");
        }
        FieldBaseline b = baselineRepo.findById(p.getId()).orElseGet(() -> {
            FieldBaseline nb = new FieldBaseline();
            nb.setPersonId(p.getId());
            return nb;
        });
        b.setHrRestMean(req.hrRestMean);
        b.setHrRestStd(req.hrRestStd);
        b.setHrActiveMean(req.hrActiveMean);
        b.setSpo2Mean(req.spo2Mean);
        b.setSpo2Std(req.spo2Std);
        b.setBodyTempMean(req.bodyTempMean);
        b.setBodyTempStd(req.bodyTempStd);
        b.setAccelBaselineMean(req.accelBaselineMean);
        b.setAccelBaselineStd(req.accelBaselineStd);
        b.setAlertHrUpper(req.alertHrUpper);
        b.setAlertHrLower(req.alertHrLower);
        b.setAlertSpo2Range(req.alertSpo2Range);
        b.setSamplesCount(req.samplesCount != null ? req.samplesCount : 0);
        b.setLastLearnedAt(LocalDateTime.now());
        baselineRepo.save(b);
        vitalBaselineService.seedFromCalibration(p.getId(), b);   // P5-W1 개인 대역 부트스트랩(최초 1회).
        return Map.of("ok", true);
    }

    /** 베이스라인 복원 (서버 → 워치). 워치 재설치 후 첫 부팅. */
    @GetMapping("/baseline/restore")
    public Map<String, Object> baselineRestore(@RequestHeader("X-Field-Token") String token, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        return baselineRepo.findById(p.getId())
                .map(this::baselineMap)
                .orElse(Map.of("exists", false));
    }

    /** 폰 BridgeService 가 폴링 (15초). 미확인 알림 최근 5건. */
    @GetMapping("/alerts/recent")
    public List<Map<String, Object>> recentAlerts(@RequestHeader("X-Field-Token") String token,
                                                  @RequestParam(defaultValue = "5") int limit, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        return alertRepo.findByPersonIdAndCreatedAtAfterOrderByCreatedAtDesc(p.getId(),
                        LocalDateTime.now().minusHours(1))
                .stream().limit(limit).map(this::alertMap).toList();
    }

    /** 본인 알림 확인 처리. */
    @PostMapping("/alerts/{id}/ack")
    @Transactional
    public Map<String, Object> ackAlert(@RequestHeader("X-Field-Token") String token,
                                        @org.springframework.web.bind.annotation.PathVariable Long id, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        FieldSafetyAlert a = alertRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "알림을 찾을 수 없습니다"));
        if (!p.getId().equals(a.getPersonId())) {
            throw ApiException.forbidden("NOT_OWN_ALERT", "본인 알림만 처리 가능합니다");
        }
        a.setResolved(true);
        a.setResolvedAt(LocalDateTime.now());
        alertRepo.save(a);
        broadcaster.publishResolved(a);
        // 대응체인 발동(peer_notified)됐던 경보 해제 → 피재자(본인) 폰 파인드미 해제. 신 경로(SafetyAlertController.resolve)와 정합.
        if (a.getPeerNotifiedAt() != null) {
            emergencyResponseService.sendFindMeStop(p);
        }
        return Map.of("ok", true);
    }

    /**
     * S5' 확인응답(ack) — 본인 대상 안전알림 [확인] 버튼. acknowledged_at·ack_person_id 기록(인지 증거).
     * resolved(관제 처리완료)와 별개. 최초 확인만 기록(멱등) → 관제 WS 실시간 갱신.
     */
    @PostMapping("/safety-alerts/{id}/ack")
    @Transactional
    public Map<String, Object> ackSafetyAlert(@RequestHeader("X-Field-Token") String token,
                                              @org.springframework.web.bind.annotation.PathVariable Long id,
                                              HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        FieldSafetyAlert a = alertRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "알림을 찾을 수 없습니다"));
        if (!p.getId().equals(a.getPersonId())) {
            throw ApiException.forbidden("NOT_OWN_ALERT", "본인 알림만 확인 가능합니다");
        }
        if (a.getAcknowledgedAt() == null) {   // 최초 확인만 기록(멱등).
            a.setAcknowledgedAt(LocalDateTime.now());
            a.setAckPersonId(p.getId());
            alertRepo.save(a);
            broadcaster.publishAcked(a);
            // P5-W1 학습 오탐 피드백 — vital 경보를 창 내 본인 확인(=이상없음) 시 개인 임계 완화.
            // 낙상·SOS·데드맨·폭염은 LEARN_KINDS 에 없음(민감도 저하 금지).
            if (VitalAnomalyService.LEARN_KINDS.contains(a.getKind())
                    && VitalBaselineService.isSelfCancel(a.getCreatedAt(), a.getAcknowledgedAt())) {
                vitalBaselineService.markFalsePositive(p.getId());
            }
        }
        return Map.of("ok", true, "acknowledged_at", a.getAcknowledgedAt());
    }

    // ───────── 내부 헬퍼 ─────────

    /** person → 현재 open AttendanceSession 의 workPlan/site/bpCompany 캐시. */
    private OpenContext openContext(Person p) {
        OpenContext ctx = new OpenContext();
        // open session 이 없으면 가장 최근 wp 기준으로 site 추적. (전체 이력 대신 최신 1건만)
        AttendanceSession s = attRepo.findFirstByPersonIdOrderByCheckInAtDesc(p.getId()).orElse(null);
        if (s != null) {
            ctx.workPlanId = s.getWorkPlanId();
            WorkPlan wp = wpRepo.findById(s.getWorkPlanId()).orElse(null);
            if (wp != null) {
                ctx.siteId = wp.getSiteId();
                ctx.bpCompanyId = wp.getBpCompanyId();
            }
        }
        return ctx;
    }

    private Map<String, Object> alertMap(FieldSafetyAlert a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("kind", a.getKind());
        m.put("level", a.getLevel());
        m.put("severity", a.getSeverity());
        m.put("message", a.getMessage());
        m.put("hr", a.getHr());
        m.put("spo2", a.getSpo2());
        m.put("resolved", a.isResolved());
        m.put("acknowledged_at", a.getAcknowledgedAt());
        m.put("created_at", a.getCreatedAt());
        return m;
    }

    private Map<String, Object> baselineMap(FieldBaseline b) {
        Map<String, Object> m = new HashMap<>();
        m.put("exists", true);
        m.put("hr_rest_mean", b.getHrRestMean());
        m.put("hr_rest_std", b.getHrRestStd());
        m.put("hr_active_mean", b.getHrActiveMean());
        m.put("spo2_mean", b.getSpo2Mean());
        m.put("spo2_std", b.getSpo2Std());
        m.put("body_temp_mean", b.getBodyTempMean());
        m.put("body_temp_std", b.getBodyTempStd());
        m.put("accel_baseline_mean", b.getAccelBaselineMean());
        m.put("accel_baseline_std", b.getAccelBaselineStd());
        m.put("alert_hr_upper", b.getAlertHrUpper());
        m.put("alert_hr_lower", b.getAlertHrLower());
        m.put("alert_spo2_range", b.getAlertSpo2Range());
        m.put("samples_count", b.getSamplesCount());
        return m;
    }

    private static class OpenContext {
        Long workPlanId;
        Long siteId;
        Long bpCompanyId;
    }

    public static class SensorRequest {
        public Integer hr;
        public Integer spo2;
        public BigDecimal bodyTemp;
        public Integer stress;
        public String state;
        public Double lat;
        public Double lng;
        // P5-W0: 워치 상태 요약(묶음 전송 시 상단 현재값).
        public Integer battery;
        public Boolean worn;
        // P5-W0: 평상 10분·주의 5분 로컬 버퍼 묶음. 없으면 상단 단건(하위호환).
        public List<SensorRecord> records;
    }

    /** P5-W0 묶음 전송 1건(로컬 버퍼 샘플). raw 저장은 record 단위, 상태 요약은 마지막 값. */
    public static class SensorRecord {
        public Integer hr;
        public Integer spo2;
        public BigDecimal bodyTemp;
        public Integer stress;
        public String state;
        public Double lat;
        public Double lng;

        static SensorRecord of(SensorRequest req) {
            SensorRecord r = new SensorRecord();
            r.hr = req.hr; r.spo2 = req.spo2; r.bodyTemp = req.bodyTemp;
            r.stress = req.stress; r.state = req.state; r.lat = req.lat; r.lng = req.lng;
            return r;
        }
    }

    public static class EmergencyRequest {
        public String kind;
        public String level;
        public String message;
        public Integer hr;
        public Integer spo2;
        public BigDecimal bodyTemp;
        public Integer stress;
        public Double lat;
        public Double lng;
    }

    /** P5-W2 동료 응답. */
    public static class RespondRequest {
        public String response;   // GOING (현재 유일값).
    }

    /**
     * P5-W3 BLE 대리중계 요청(FieldApi.kt:165-177 계약).
     * alertId(-1 가능)·rssi 는 폰 근접 게이지용 — 서버는 victimPersonId·relayLat/Lng 만 사용.
     */
    public static class SosRelayRequest {
        public Long victimPersonId;
        public Long alertId;
        public Integer rssi;
        public Double relayLat;
        public Double relayLng;
    }

    public static class BaselineRequest {
        public BigDecimal hrRestMean;
        public BigDecimal hrRestStd;
        public BigDecimal hrActiveMean;
        public BigDecimal spo2Mean;
        public BigDecimal spo2Std;
        public BigDecimal bodyTempMean;
        public BigDecimal bodyTempStd;
        public BigDecimal accelBaselineMean;
        public BigDecimal accelBaselineStd;
        public BigDecimal alertHrUpper;
        public BigDecimal alertHrLower;
        public BigDecimal alertSpo2Range;
        public Integer samplesCount;
    }
}
