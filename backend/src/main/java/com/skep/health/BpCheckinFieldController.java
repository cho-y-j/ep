package com.skep.health;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.field.FieldTokenAuth;
import com.skep.field.FieldTokenRateLimiter;
import com.skep.person.Person;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * P5-W4 1겹 — 작업자 본인 혈압 셀프 체크인. X-Field-Token(출근코드) 인증.
 * 현장/BP 는 본인 최근 출근 세션에서 해석. verdict 는 서버 계산(BLOCK 이어도 출근 차단은 없음 — 권고).
 */
@RestController
@RequestMapping("/api/field-auth/bp-checkin")
@RequiredArgsConstructor
public class BpCheckinFieldController {

    private final FieldTokenAuth fieldAuth;
    private final FieldTokenRateLimiter rateLimiter;
    private final AttendanceSessionRepository attRepo;
    private final WorkPlanRepository wpRepo;
    private final BpCheckinService service;

    @PostMapping
    @Transactional
    public Map<String, Object> checkin(@RequestHeader("X-Field-Token") String token,
                                       @RequestBody SelfBpRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);

        Long siteId = null, bpCompanyId = null;
        AttendanceSession s = attRepo.findFirstByPersonIdOrderByCheckInAtDesc(p.getId()).orElse(null);
        if (s != null) {
            WorkPlan wp = wpRepo.findById(s.getWorkPlanId()).orElse(null);
            if (wp != null) {
                siteId = wp.getSiteId();
                bpCompanyId = wp.getBpCompanyId();
            }
        }
        BpCheckin c = service.recordSelf(p, siteId, bpCompanyId, req.sys, req.dia, req.pulse, req.method);

        Map<String, Object> out = new HashMap<>();
        out.put("id", c.getId());
        out.put("verdict", c.getVerdict().name());
        out.put("recommendation", recommendation(c.getVerdict()));
        return out;
    }

    private static String recommendation(BpVerdict v) {
        return switch (v) {
            case BLOCK -> "혈압이 높습니다. 고소·고강도 작업을 피하고 관리자에게 알리세요.";
            case CAUTION -> "혈압이 다소 높습니다. 무리하지 말고 휴식 후 재측정하세요.";
            case OK -> "정상 범위입니다.";
        };
    }

    public static class SelfBpRequest {
        public Integer sys;
        public Integer dia;
        public Integer pulse;
        public String method;   // MANUAL | BLE
    }
}
