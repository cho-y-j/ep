package com.skep.attendance;

import com.skep.common.ApiException;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceSessionRepository repo;
    private final PersonRepository personRepo;
    private final WorkPlanPersonRepository wppRepo;
    private final WorkPlanRepository wpRepo;
    private final com.skep.site.SiteRepository siteRepo;
    private final com.skep.workplan.WorkPlanService workPlanService;

    /** 인원이 현재 활성 wp 중 자기 인원이 등록된 것 + 현재 출근 상태. 공급사 마스터/ADMIN 이 자기 회사 인원의 페이지 진입 용도. */
    @GetMapping("/by-person/{personId}/active")
    public List<Map<String, Object>> byPersonActive(@PathVariable Long personId,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        ensureCanCheck(actor, personId);
        var statuses = List.of(WorkPlanStatus.SUBMITTED, WorkPlanStatus.APPROVED, WorkPlanStatus.IN_PROGRESS, WorkPlanStatus.DONE);
        var wpps = wppRepo.findByPersonId(personId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkPlanPerson wpp : wpps) {
            WorkPlan wp = wpRepo.findById(wpp.getWorkPlanId()).orElse(null);
            if (wp == null || !statuses.contains(wp.getStatus())) continue;
            var open = repo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(personId, wp.getId());
            Map<String, Object> m = new HashMap<>();
            m.put("work_plan_id", wp.getId());
            m.put("work_plan_title", wp.getTitle());
            m.put("work_date", wp.getWorkDate());
            m.put("site_id", wp.getSiteId());
            m.put("person_id", personId);
            m.put("open_session_id", open.map(AttendanceSession::getId).orElse(null));
            m.put("check_in_at", open.map(AttendanceSession::getCheckInAt).orElse(null));
            out.add(m);
        }
        return out;
    }

    @PostMapping("/check-in")
    @Transactional
    public Map<String, Object> checkIn(@RequestBody CheckInRequest req,
                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        if (req.personId == null) throw ApiException.badRequest("NO_PERSON", "person_id 필수");
        Long personId = req.personId;
        ensureCanCheck(actor, personId);
        if (req.workPlanId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");

        // 미완료 세션 있으면 차단
        var open = repo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(personId, req.workPlanId);
        if (open.isPresent()) {
            throw ApiException.badRequest("ALREADY_CHECKED_IN", "이미 출근 상태입니다 (퇴근 체크아웃 먼저 하세요)");
        }
        AttendanceSession row = AttendanceSession.builder()
                .personId(personId)
                .workPlanId(req.workPlanId)
                .checkInPhotoDocId(req.photoDocId)
                .checkInLat(req.lat)
                .checkInLng(req.lng)
                .build();
        repo.save(row);
        return toMap(row);
    }

    @PostMapping("/check-out")
    @Transactional
    public Map<String, Object> checkOut(@RequestBody CheckOutRequest req,
                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        AttendanceSession row;
        if (req.sessionId != null) {
            row = repo.findById(req.sessionId).orElseThrow(() ->
                    ApiException.notFound("SESSION_NOT_FOUND", "세션 없음"));
        } else {
            if (req.personId == null) throw ApiException.badRequest("NO_PERSON", "person_id 필수");
            Long personId = req.personId;
            if (req.workPlanId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");
            row = repo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(personId, req.workPlanId)
                    .orElseThrow(() -> ApiException.badRequest("NO_OPEN_SESSION", "체크인 상태가 아닙니다"));
        }
        ensureCanCheck(actor, row.getPersonId());
        if (row.getCheckOutAt() != null) {
            throw ApiException.badRequest("ALREADY_CHECKED_OUT", "이미 퇴근 처리됨");
        }
        row.checkOut(req.photoDocId, null, req.lat, req.lng);
        return toMap(row);
    }

    @GetMapping("/by-work-plan/{wpId}")
    public List<Map<String, Object>> listByWorkPlan(@PathVariable Long wpId,
                                                    @AuthenticationPrincipal AuthenticatedUser actor) {
        // 작업계획서 가시성(ADMIN/BP 자기회사/공급사 참여) 검증을 WorkPlanService 에 위임 — 통과 못하면 throw.
        workPlanService.get(wpId, actor);
        return repo.findByWorkPlanIdOrderByCheckInAtDesc(wpId).stream().map(this::toMap).toList();
    }

    /** 작업시간 지정·수정 — 현장 관리자(ADMIN/BP)만. 휴식 알림 기준이 됨. */
    @PatchMapping("/{id}/work-time")
    @Transactional
    public Map<String, Object> setWorkTime(@PathVariable Long id,
                                           @RequestBody WorkTimeRequest req,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        AttendanceSession row = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("SESSION_NOT_FOUND", "세션 없음"));
        ensureManager(actor, row.getWorkPlanId());
        LocalDateTime start = parseDt(req.workStartAt);
        LocalDateTime end = parseDt(req.workEndAt);
        if (start != null && end != null && end.isBefore(start)) {
            throw ApiException.badRequest("BAD_RANGE", "종료가 시작보다 빠릅니다");
        }
        row.setWorkTime(start, end);
        return toMap(row);
    }

    /** 출근 중(미퇴근) 세션 목록 — ADMIN 전체 / BP 자기 현장. 작업시간 지정 UI 용. */
    @GetMapping("/open")
    public List<Map<String, Object>> listOpen(@AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("DENIED", "ADMIN/BP 전용");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AttendanceSession s : repo.findByCheckOutAtIsNull()) {
            WorkPlan wp = wpRepo.findById(s.getWorkPlanId()).orElse(null);
            if (wp == null) continue;
            if (actor.role() == Role.BP && !wp.getBpCompanyId().equals(actor.companyId())) continue;
            Map<String, Object> m = toMap(s);
            m.put("person_name", personRepo.findById(s.getPersonId()).map(Person::getName).orElse("#" + s.getPersonId()));
            m.put("site_id", wp.getSiteId());
            m.put("site_name", wp.getSiteId() != null
                    ? siteRepo.findById(wp.getSiteId()).map(com.skep.site.Site::getName).orElse(null) : null);
            m.put("work_plan_title", wp.getTitle());
            out.add(m);
        }
        return out;
    }

    private void ensureManager(AuthenticatedUser actor, Long wpId) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            WorkPlan wp = wpRepo.findById(wpId).orElse(null);
            if (wp != null && wp.getBpCompanyId().equals(actor.companyId())) return;
        }
        throw ApiException.forbidden("DENIED", "작업시간 지정은 ADMIN/BP만 가능합니다");
    }

    private static LocalDateTime parseDt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.trim());
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_TIME", "시간 형식 오류 (ISO LocalDateTime)");
        }
    }

    // ──────────────────────────────────────────────────────────────────

    private void ensureCanCheck(AuthenticatedUser actor, Long personId) {
        if (actor.role() == Role.ADMIN) return;
        Person p = personRepo.findById(personId).orElseThrow(() ->
                ApiException.notFound("PERSON_NOT_FOUND", "인원 없음"));
        // 공급사 마스터: 자기 회사 인원만
        if (p.getSupplierId() != null && p.getSupplierId().equals(actor.companyId())) return;
        // BP: 자기 회사 작업계획서에 그 인원이 등록된 경우 OK
        if (actor.role() == Role.BP) {
            var wpps = wppRepo.findByPersonId(personId);
            for (var wpp : wpps) {
                WorkPlan wp = wpRepo.findById(wpp.getWorkPlanId()).orElse(null);
                if (wp != null && wp.getBpCompanyId().equals(actor.companyId())) return;
            }
        }
        throw ApiException.forbidden("DENIED", "출퇴근 처리 권한 없음");
    }

    private Map<String, Object> toMap(AttendanceSession s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("person_id", s.getPersonId());
        m.put("work_plan_id", s.getWorkPlanId());
        m.put("check_in_at", s.getCheckInAt());
        m.put("check_out_at", s.getCheckOutAt());
        m.put("check_in_photo_doc_id", s.getCheckInPhotoDocId());
        m.put("check_out_photo_doc_id", s.getCheckOutPhotoDocId());
        m.put("hours", s.getCheckOutAt() != null
                ? java.time.Duration.between(s.getCheckInAt(), s.getCheckOutAt()).toMinutes() / 60.0
                : null);
        m.put("work_start_at", s.getWorkStartAt());
        m.put("work_end_at", s.getWorkEndAt());
        return m;
    }

    public static class CheckInRequest {
        public Long personId;
        public Long workPlanId;
        public Long photoDocId;
        public Double lat;
        public Double lng;
    }
    public static class CheckOutRequest {
        public Long sessionId;
        public Long personId;
        public Long workPlanId;
        public Long photoDocId;
        public Double lat;
        public Double lng;
    }
    public static class WorkTimeRequest {
        public String workStartAt;  // ISO LocalDateTime (예: 2026-06-15T08:00:00) 또는 null
        public String workEndAt;
    }
}
