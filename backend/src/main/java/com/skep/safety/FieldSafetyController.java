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

    /** 5분 주기 센서 데이터. raw 저장만 (분석/그래프용). */
    @PostMapping("/sensor")
    @Transactional
    public Map<String, Object> sensor(@RequestHeader("X-Field-Token") String token,
                                      @RequestBody SensorRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        var ctx = openContext(p);
        FieldSensorReading r = new FieldSensorReading();
        r.setPersonId(p.getId());
        r.setWorkPlanId(ctx.workPlanId);
        r.setSiteId(ctx.siteId);
        r.setHr(req.hr);
        r.setSpo2(req.spo2);
        r.setBodyTemp(req.bodyTemp);
        r.setStress(req.stress);
        r.setState(req.state);
        r.setLat(req.lat);
        r.setLng(req.lng);
        sensorRepo.save(r);
        return Map.of("ok", true, "id", r.getId());
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
        return Map.of("ok", true, "alert_id", a.getId());
    }

    /** 베이스라인 동기화 (워치 → 서버). 30분 주기. */
    @PostMapping("/baseline/sync")
    @Transactional
    public Map<String, Object> baselineSync(@RequestHeader("X-Field-Token") String token,
                                            @RequestBody BaselineRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
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
