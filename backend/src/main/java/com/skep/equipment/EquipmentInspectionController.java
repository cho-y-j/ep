package com.skep.equipment;

import com.skep.common.ApiException;
import com.skep.field.FieldTokenAuth;
import com.skep.field.FieldTokenRateLimiter;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.Role;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 차량 일상점검 — 조종원(폰) 제출 + 관리(웹) 이력 조회 + 차량 due 날짜 설정. */
@RestController
@RequiredArgsConstructor
public class EquipmentInspectionController {

    private final DailyEquipmentInspectionRepository repo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final FieldTokenAuth fieldAuth;
    private final FieldTokenRateLimiter rateLimiter;
    private final com.skep.site.SiteRepository siteRepo;
    private final com.skep.company.CompanyService companyService;

    /** 작업자(폰, X-Field-Token) 일상점검 제출. */
    @PostMapping("/api/field-auth/equipment-inspection")
    @Transactional
    public Map<String, Object> submit(@RequestHeader("X-Field-Token") String token,
                                      @RequestBody SubmitRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (req.equipmentId == null) throw ApiException.badRequest("NO_EQUIPMENT", "equipment_id 필수");
        Equipment e = equipmentRepo.findById(req.equipmentId).orElseThrow(() ->
                ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 없음"));
        // 임의 equipment_id 점검 위조 차단 — 작업자 소속 공급사 장비만 점검 제출 가능.
        if (p.getSupplierId() == null || !p.getSupplierId().equals(e.getSupplierId())) {
            throw ApiException.forbidden("DENIED", "본인 소속 공급사 장비만 점검할 수 있습니다");
        }
        DailyEquipmentInspection row = new DailyEquipmentInspection();
        row.setEquipmentId(e.getId());
        row.setInspectedByPersonId(p.getId());
        LocalDate d = parseDate(req.inspectDate);
        row.setInspectDate(d != null ? d : LocalDate.now());
        row.setItems(req.items);
        row.setPhotoKey(req.photoKey);
        row.setNotes(req.notes);
        row.setOverall(req.overall != null && !req.overall.isBlank() ? req.overall : "PASS");
        row.setHourMeter(req.hourMeter);
        row.setOdometerKm(req.odometerKm);
        repo.save(row);
        return map(row, p.getName());
    }

    /** 장비 일상점검 이력 — ADMIN/BP 전체, 공급사 본인 장비. */
    @GetMapping("/api/equipment/{id}/daily-inspections")
    public List<Map<String, Object>> list(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Equipment e = equipmentRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 없음"));
        ensureCanView(actor, e);
        Map<Long, String> names = new HashMap<>();
        return repo.findByEquipmentIdOrderByInspectDateDescIdDesc(id).stream()
                .map(r -> map(r, r.getInspectedByPersonId() != null
                        ? names.computeIfAbsent(r.getInspectedByPersonId(),
                            pid -> personRepo.findById(pid).map(Person::getName).orElse("#" + pid))
                        : null))
                .toList();
    }

    /** 차량 due(정기검사/오일교체/등록만료) 설정 — ADMIN 또는 본인 공급사. */
    @PatchMapping("/api/equipment/{id}/due-dates")
    @Transactional
    public Map<String, Object> setDueDates(@PathVariable Long id, @RequestBody DueRequest req,
                                           @CurrentUser AuthenticatedUser actor) {
        Equipment e = equipmentRepo.findById(id).orElseThrow(() ->
                ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 없음"));
        ensureCanEdit(actor, e);
        e.setDueDates(parseDate(req.inspectionDueDate), parseDate(req.oilChangeDueDate), parseDate(req.registrationExpiry));
        equipmentRepo.save(e);
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("inspection_due_date", e.getInspectionDueDate());
        m.put("oil_change_due_date", e.getOilChangeDueDate());
        m.put("registration_expiry", e.getRegistrationExpiry());
        return m;
    }

    private void ensureCanView(AuthenticatedUser actor, Equipment e) {
        if (actor.role() == Role.ADMIN) return;
        // 공급사: 본인+직속 자식(협력사) 소유 장비. 장비 GET·combo 판정과 스코프 정합.
        if (actor.companyId() != null && companyService.selfAndChildren(actor.companyId()).contains(e.getSupplierId())) return;
        // BP: 장비가 현재 자기 회사 현장에 배치된 경우만 (타사 장비 점검이력 열람 차단).
        if (actor.role() == Role.BP && actor.companyId() != null && e.getCurrentSiteId() != null) {
            Long bp = siteRepo.findById(e.getCurrentSiteId())
                    .map(com.skep.site.Site::getBpCompanyId).orElse(null);
            if (actor.companyId().equals(bp)) return;
        }
        throw ApiException.forbidden("DENIED", "조회 권한 없음");
    }

    private void ensureCanEdit(AuthenticatedUser actor, Equipment e) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.companyId() != null && actor.companyId().equals(e.getSupplierId())) return;
        throw ApiException.forbidden("DENIED", "본인 회사 장비만 수정 가능");
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception ex) {
            throw ApiException.badRequest("BAD_DATE", "날짜 형식 오류 (YYYY-MM-DD)");
        }
    }

    private Map<String, Object> map(DailyEquipmentInspection r, String inspectorName) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("equipment_id", r.getEquipmentId());
        m.put("inspected_by_person_id", r.getInspectedByPersonId());
        m.put("inspector_name", inspectorName);
        m.put("inspect_date", r.getInspectDate());
        m.put("items", r.getItems());
        m.put("photo_key", r.getPhotoKey());
        m.put("notes", r.getNotes());
        m.put("overall", r.getOverall());
        m.put("hour_meter", r.getHourMeter());
        m.put("odometer_km", r.getOdometerKm());
        m.put("created_at", r.getCreatedAt());
        return m;
    }

    public static class SubmitRequest {
        public Long equipmentId;
        public String inspectDate;  // YYYY-MM-DD, null=오늘
        public String items;        // 체크리스트 JSON 문자열
        public String photoKey;
        public String notes;
        public String overall;      // PASS|ATTENTION|FAIL
        public java.math.BigDecimal hourMeter;   // 가동시간(아워미터), 선택
        public java.math.BigDecimal odometerKm;  // 운행거리(km), 선택
    }
    public static class DueRequest {
        public String inspectionDueDate;
        public String oilChangeDueDate;
        public String registrationExpiry;
    }
}
