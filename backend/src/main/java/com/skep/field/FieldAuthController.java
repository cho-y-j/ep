package com.skep.field;

import com.skep.attendance.AttendanceSession;
import com.skep.attendance.AttendanceSessionRepository;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.storage.FileStorage;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.workconfirmation.IssuingSupplierType;
import com.skep.workconfirmation.WorkConfirmation;
import com.skep.workconfirmation.WorkConfirmationRepository;
import com.skep.workconfirmation.WorkConfirmationStatus;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanPerson;
import com.skep.workplan.WorkPlanPersonRepository;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/field-auth")
@RequiredArgsConstructor
public class FieldAuthController {

    private final PersonRepository personRepo;
    private final EquipmentRepository equipmentRepo;
    private final AttendanceSessionRepository attRepo;
    private final WorkPlanPersonRepository wppRepo;
    private final WorkPlanRepository wpRepo;
    private final SiteRepository sites;
    private final CompanyRepository companies;
    private final FileStorage storage;
    private final FieldFcmService fcm;
    private final WorkConfirmationRepository wcRepo;
    private final FieldTokenAuth fieldAuth;
    private final NotificationService notifications;
    private final UserRepository users;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final FieldTokenRateLimiter rateLimiter;

    @PostMapping("/auth")
    public Map<String, Object> auth(@RequestBody AuthRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        if (req.code == null || req.code.isBlank()) {
            throw ApiException.badRequest("NO_CODE", "코드 입력 필수");
        }
        Person p = personRepo.findByAttendanceCode(req.code.trim()).orElseThrow(() ->
                ApiException.forbidden("INVALID_CODE", "코드가 올바르지 않습니다"));
        return personMap(p);
    }

    /** POST /api/field-auth/login { username, password } — 공급사 발급 계정 로그인. 성공 시 기존 출근코드 토큰 반환. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        if (req.username == null || req.username.isBlank() || req.password == null || req.password.isBlank()) {
            throw ApiException.badRequest("NO_CREDENTIALS", "아이디/비밀번호를 입력하세요");
        }
        Person p = personRepo.findByUsername(req.username.trim()).orElse(null);
        if (p == null || p.getPasswordHash() == null
                || !passwordEncoder.matches(req.password, p.getPasswordHash())) {
            throw ApiException.forbidden("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다");
        }
        return personMap(p);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader("X-Field-Token") String token, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        Map<String, Object> out = personMap(p);
        out.put("active_work_plans", listActiveWorkPlans(p.getId()));
        return out;
    }

    /** NFC(RFC) 태그 → 자원 식별. 작업자 카드(PERSON) 또는 차량 태그(EQUIPMENT). 인증된 작업자만. */
    @GetMapping("/nfc/{tagId}")
    public Map<String, Object> resolveNfc(@RequestHeader("X-Field-Token") String token,
                                          @PathVariable String tagId, HttpServletRequest request) {
        rateLimiter.check(request);
        fieldAuth.authenticate(token);
        String tag = tagId == null ? "" : tagId.trim();
        var person = personRepo.findByNfcTagId(tag);
        if (person.isPresent()) {
            Person pp = person.get();
            return Map.of("type", "PERSON", "id", pp.getId(), "label", pp.getName());
        }
        var eq = equipmentRepo.findByNfcTagId(tag);
        if (eq.isPresent()) {
            var e = eq.get();
            String label = e.getVehicleNo() != null ? e.getVehicleNo()
                    : (e.getModel() != null ? e.getModel() : "장비#" + e.getId());
            Map<String, Object> m = new HashMap<>();
            m.put("type", "EQUIPMENT");
            m.put("id", e.getId());
            m.put("label", label);
            m.put("category", e.getCategory());
            return m;
        }
        throw ApiException.notFound("NFC_NOT_REGISTERED", "등록되지 않은 태그입니다");
    }

    /** 데모용 — 본인 폰으로 본인에게 공지 푸시. firebase-admin-key.json 있어야 실제 발송. */
    @PostMapping("/announce-test")
    public Map<String, Object> announceTest(@RequestHeader("X-Field-Token") String token,
                                            @RequestBody AnnounceTestRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (p.getFcmToken() == null || p.getFcmToken().isBlank()) {
            throw ApiException.badRequest("NO_FCM", "FCM 토큰 미등록 — 앱에서 한 번 로그인 필요");
        }
        if (req.title == null || req.title.isBlank()) throw ApiException.badRequest("NO_TITLE", "title 필수");
        if (req.body == null || req.body.isBlank()) throw ApiException.badRequest("NO_BODY", "body 필수");
        int sent = fcm.sendAnnouncement(java.util.List.of(p.getFcmToken()), req.title, req.body);
        return Map.of("attempted", sent, "person_id", p.getId(), "name", p.getName());
    }

    @PostMapping("/register-token")
    @Transactional
    public Map<String, Object> registerToken(@RequestHeader("X-Field-Token") String token,
                                             @RequestBody RegisterTokenRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (req.fcmToken == null || req.fcmToken.isBlank()) {
            throw ApiException.badRequest("NO_FCM_TOKEN", "fcm_token 필수");
        }
        p.updateFcmToken(req.fcmToken.trim());
        personRepo.save(p);
        return Map.of("ok", true);
    }

    @PostMapping("/register-watch-token")
    @Transactional
    public Map<String, Object> registerWatchToken(@RequestHeader("X-Field-Token") String token,
                                                  @RequestBody RegisterTokenRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (req.fcmToken == null || req.fcmToken.isBlank()) {
            throw ApiException.badRequest("NO_FCM_TOKEN", "fcm_token 필수");
        }
        p.updateWatchFcmToken(req.fcmToken.trim());
        personRepo.save(p);
        return Map.of("ok", true);
    }

    @PostMapping("/check-in")
    @Transactional
    public ResponseEntity<Map<String, Object>> checkIn(@RequestHeader("X-Field-Token") String token,
                                       @RequestBody CheckInRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (req.workPlanId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");
        // 서버측 지오펜스 — 현장 중심좌표가 설정된 경우 출근 위치를 검증(GPS 위조 차단).
        WorkPlan wp = wpRepo.findById(req.workPlanId).orElse(null);
        Site site = wp != null && wp.getSiteId() != null ? sites.findById(wp.getSiteId()).orElse(null) : null;
        if (site != null && site.getLatitude() != null && site.getLongitude() != null) {
            int radius = site.getGeofenceRadiusM() != null ? site.getGeofenceRadiusM() : 300;
            if (req.lat == null || req.lng == null) {
                return ResponseEntity.status(403).body(Map.of("code", "OUT_OF_SITE", "distance_m", -1));
            }
            int distance = (int) Math.round(haversineMeters(
                    site.getLatitude(), site.getLongitude(), req.lat, req.lng));
            if (distance > radius) {
                return ResponseEntity.status(403).body(Map.of("code", "OUT_OF_SITE", "distance_m", distance));
            }
        }
        var open = attRepo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(p.getId(), req.workPlanId);
        if (open.isPresent()) {
            throw ApiException.badRequest("ALREADY_CHECKED_IN", "이미 출근 상태입니다");
        }
        AttendanceSession row = AttendanceSession.builder()
                .personId(p.getId())
                .workPlanId(req.workPlanId)
                .checkInPhotoDocId(req.photoDocId)
                .checkInPhotoKey(req.photoKey)
                .checkInLat(req.lat)
                .checkInLng(req.lng)
                .checkInMethod(req.method)
                .build();
        attRepo.save(row);
        return ResponseEntity.ok(sessionMap(row));
    }

    /** 두 좌표 간 haversine 거리(m). */
    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    @PostMapping("/break/start")
    @Transactional
    public Map<String, Object> startBreak(@RequestHeader("X-Field-Token") String token,
                                          @RequestBody(required = false) Map<String, Object> req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        Long wpId = req != null && req.get("work_plan_id") != null
                ? Long.valueOf(req.get("work_plan_id").toString()) : null;
        if (wpId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");
        var open = attRepo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(p.getId(), wpId)
                .orElseThrow(() -> ApiException.badRequest("NO_OPEN_SESSION", "출근 상태가 아닙니다"));
        if (open.getBreakStartAt() != null) {
            throw ApiException.badRequest("ALREADY_ON_BREAK", "이미 휴식 중입니다");
        }
        open.startBreak();
        return sessionMap(open);
    }

    @PostMapping("/break/end")
    @Transactional
    public Map<String, Object> endBreak(@RequestHeader("X-Field-Token") String token,
                                        @RequestBody(required = false) Map<String, Object> req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        Long wpId = req != null && req.get("work_plan_id") != null
                ? Long.valueOf(req.get("work_plan_id").toString()) : null;
        if (wpId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");
        var open = attRepo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(p.getId(), wpId)
                .orElseThrow(() -> ApiException.badRequest("NO_OPEN_SESSION", "출근 상태가 아닙니다"));
        if (open.getBreakStartAt() == null) {
            throw ApiException.badRequest("NOT_ON_BREAK", "휴식 상태가 아닙니다");
        }
        open.endBreak();
        return sessionMap(open);
    }

    @PostMapping("/check-out")
    @Transactional
    public Map<String, Object> checkOut(@RequestHeader("X-Field-Token") String token,
                                        @RequestBody CheckOutRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (req.workPlanId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");
        var open = attRepo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(p.getId(), req.workPlanId)
                .orElseThrow(() -> ApiException.badRequest("NO_OPEN_SESSION", "출근 상태가 아닙니다"));
        open.checkOut(req.photoDocId, req.photoKey, req.lat, req.lng);
        ensureWorkConfirmation(p, open);
        return sessionMap(open);
    }

    /** 퇴근 시점에 작업확인서를 자동 생성. 총시간 = 근무시간 - max(기록된 휴식, 1h 점심).
     *  같은 날 2번째 이후 출퇴근분은 자동 생성된(미서명·슬롯 미입력) 작업확인서에 합산. */
    private void ensureWorkConfirmation(Person p, AttendanceSession s) {
        WorkPlan wp = wpRepo.findById(s.getWorkPlanId()).orElse(null);
        if (wp == null) return;
        double workedHours = java.time.Duration.between(s.getCheckInAt(), s.getCheckOutAt()).toMinutes() / 60.0;
        double recordedBreak = (s.getBreakMinutes() == null ? 0 : s.getBreakMinutes()) / 60.0;
        // 점심 1h 항상 차감 — 휴식 미기록 시 1h 과다 산정 방지. 기록된 휴식이 더 길면 그 값 사용.
        double net = Math.max(0, workedHours - Math.max(recordedBreak, 1.0));
        var existing = wcRepo.findByWorkPlanIdAndPersonId(wp.getId(), p.getId());
        if (existing.isPresent()) {
            WorkConfirmation wc = existing.get();
            boolean autoGenerated = wc.getStatus() == WorkConfirmationStatus.PENDING
                    && wc.getSupplierSignedAt() == null && wc.getBpSignedAt() == null
                    && wc.getMorningHours() == null && wc.getAfternoonHours() == null
                    && wc.getOvertimeHours() == null && wc.getNightHours() == null;
            if (!autoGenerated) return; // 수동 수정·서명된 확인서는 보존
            java.math.BigDecimal prev = wc.getTotalHours() == null ? java.math.BigDecimal.ZERO : wc.getTotalHours();
            wc.setTotalHours(prev.add(java.math.BigDecimal.valueOf(net)).setScale(2, java.math.RoundingMode.HALF_UP));
            wcRepo.save(wc);
            return;
        }
        Long supplierId = p.getSupplierId();
        IssuingSupplierType type = IssuingSupplierType.EQUIPMENT;
        if (supplierId != null) {
            Company c = companies.findById(supplierId).orElse(null);
            if (c != null && c.getType() == CompanyType.MANPOWER) type = IssuingSupplierType.MANPOWER;
        }
        WorkConfirmation wc = new WorkConfirmation();
        wc.setWorkPlanId(wp.getId());
        wc.setPersonId(p.getId());
        wc.setWorkDate(s.getCheckInAt().toLocalDate());
        wc.setBpCompanyId(wp.getBpCompanyId());
        wc.setIssuingSupplierCompanyId(supplierId != null ? supplierId : wp.getBpCompanyId());
        wc.setIssuingSupplierType(type);
        wc.setTotalHours(java.math.BigDecimal.valueOf(net).setScale(2, java.math.RoundingMode.HALF_UP));
        wc.setStatus(WorkConfirmationStatus.PENDING);
        wcRepo.save(wc);
    }

    /** 본인 출/퇴근 기록 (최근순). work_plan 제목 + site 명 포함. */
    @GetMapping("/my-attendance")
    public List<Map<String, Object>> myAttendance(@RequestHeader("X-Field-Token") String token, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        var sessions = attRepo.findByPersonIdOrderByCheckInAtDesc(p.getId());
        // N+1 회피 — 세션들의 work_plan/site 를 한 번에 로드.
        Map<Long, WorkPlan> wpById = new HashMap<>();
        wpRepo.findAllById(sessions.stream().map(AttendanceSession::getWorkPlanId).distinct().toList())
                .forEach(w -> wpById.put(w.getId(), w));
        Map<Long, Site> siteById = new HashMap<>();
        sites.findAllById(wpById.values().stream().map(WorkPlan::getSiteId)
                        .filter(Objects::nonNull).distinct().toList())
                .forEach(st -> siteById.put(st.getId(), st));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AttendanceSession s : sessions) {
            WorkPlan wp = wpById.get(s.getWorkPlanId());
            Site site = wp != null && wp.getSiteId() != null ? siteById.get(wp.getSiteId()) : null;
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("work_plan_id", s.getWorkPlanId());
            m.put("wp_title", wp != null ? wp.getTitle() : null);
            m.put("site_name", site != null ? site.getName() : null);
            m.put("check_in_at", s.getCheckInAt());
            m.put("check_out_at", s.getCheckOutAt());
            m.put("hours", s.getCheckOutAt() != null
                    ? java.time.Duration.between(s.getCheckInAt(), s.getCheckOutAt()).toMinutes() / 60.0
                    : null);
            out.add(m);
        }
        return out;
    }

    /** 본인 작업확인서 목록 — status 무관, 최근순. */
    @GetMapping("/work-confirmations")
    public List<Map<String, Object>> myWorkConfirmations(@RequestHeader("X-Field-Token") String token, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        return wcRepo.findByPersonIdOrderByWorkDateDescIdDesc(p.getId()).stream().map(this::wcMap).toList();
    }

    /** 본인 사인. totalHours 보정 + supplier_signature_png 저장. */
    @PostMapping("/work-confirmations/{id}/sign")
    @Transactional
    public Map<String, Object> signWorkConfirmation(@RequestHeader("X-Field-Token") String token,
                                                    @org.springframework.web.bind.annotation.PathVariable Long id,
                                                    @RequestBody SignWcRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        WorkConfirmation wc = wcRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("WC_NOT_FOUND", "작업확인서를 찾을 수 없습니다"));
        if (!p.getId().equals(wc.getPersonId())) {
            throw ApiException.forbidden("NOT_OWN_WC", "본인 작업확인서만 사인 가능합니다");
        }
        if (req.totalHours != null) {
            if (req.totalHours < 0 || req.totalHours > 24) {
                throw ApiException.badRequest("HOURS_OUT_OF_RANGE", "총 시간은 0~24 사이여야 합니다");
            }
            wc.setTotalHours(java.math.BigDecimal.valueOf(req.totalHours).setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (req.remarks != null) wc.setRemarks(req.remarks);
        if (req.signaturePngBase64 == null || req.signaturePngBase64.isBlank()) {
            throw ApiException.badRequest("NO_SIGNATURE", "사인 이미지 필수");
        }
        try {
            byte[] sig = java.util.Base64.getDecoder().decode(req.signaturePngBase64);
            wc.setSupplierSignaturePng(sig);
            wc.setSupplierSignerName(p.getName());
            wc.setSupplierSignerPersonId(p.getId());
            wc.setSupplierSignedAt(java.time.LocalDateTime.now());
            if (wc.getBpSignedAt() != null) wc.setStatus(WorkConfirmationStatus.COMPLETED);
            wcRepo.save(wc);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_SIGNATURE", "사인 이미지 base64 디코딩 실패");
        }
        return wcMap(wc);
    }

    /** 작업자 → BP사 현장 문제 신고 (인원/장비). BP 웹 종 알림 + BP 폰 FCM 푸시. 본인이 배정된 작업계획서만. */
    @PostMapping("/issue-report")
    @Transactional
    public Map<String, Object> issueReport(@RequestHeader("X-Field-Token") String token,
                                           @RequestBody IssueReportRequest req, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (req.workPlanId == null) throw ApiException.badRequest("NO_WP", "work_plan_id 필수");
        if (req.message == null || req.message.isBlank()) throw ApiException.badRequest("NO_MESSAGE", "내용 필수");
        // 본인이 배정된 작업계획서만 — 타 현장 BP 로의 스팸 차단.
        boolean assigned = wppRepo.findByPersonId(p.getId()).stream()
                .anyMatch(wpp -> req.workPlanId.equals(wpp.getWorkPlanId()));
        if (!assigned) throw ApiException.forbidden("NOT_ASSIGNED", "배정된 작업이 아닙니다");
        WorkPlan wp = wpRepo.findById(req.workPlanId)
                .orElseThrow(() -> ApiException.notFound("WP_NOT_FOUND", "작업계획서를 찾을 수 없습니다"));
        Long bpCompanyId = wp.getBpCompanyId();
        if (bpCompanyId == null) throw ApiException.badRequest("NO_BP", "현장 발주사를 찾을 수 없습니다");
        String catLabel = switch (req.category == null ? "" : req.category) {
            case "PERSON" -> "인원 문제";
            case "EQUIPMENT" -> "장비 문제";
            default -> "현장 문제";
        };
        Site site = wp.getSiteId() != null ? sites.findById(wp.getSiteId()).orElse(null) : null;
        String title = "[" + catLabel + "] " + p.getName();
        String body = (site != null ? site.getName() + " — " : "") + req.message.trim();
        // 1) BP 웹 종 알림 (회사 broadcast — ADMIN 도 자동 수신).
        notifications.sendToCompany(bpCompanyId, "ISSUE_REPORT", title, body,
                "WORK_PLAN", wp.getId(), wp.getSiteId());
        // 2) BP 폰 FCM 푸시 — BP 회사 사용자 중 토큰 등록된 사람.
        List<String> tokens = users.findByCompanyIdAndFcmTokenIsNotNull(bpCompanyId).stream()
                .map(User::getFcmToken).filter(t -> t != null && !t.isBlank()).toList();
        fcm.sendAnnouncement(tokens, title, body);
        return Map.of("ok", true, "bp_company_id", bpCompanyId, "pushed", tokens.size());
    }

    private Map<String, Object> wcMap(WorkConfirmation wc) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", wc.getId());
        m.put("work_plan_id", wc.getWorkPlanId());
        m.put("work_date", wc.getWorkDate());
        m.put("total_hours", wc.getTotalHours());
        m.put("status", wc.getStatus());
        m.put("supplier_signed_at", wc.getSupplierSignedAt());
        m.put("bp_signed_at", wc.getBpSignedAt());
        m.put("remarks", wc.getRemarks());
        return m;
    }

    /** 출/퇴근 사진 업로드 — 파일 key 반환. 이후 check-in/out 호출 시 photo_key 로 전달. */
    @PostMapping(value = "/upload-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadPhoto(@RequestHeader("X-Field-Token") String token,
                                           @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        rateLimiter.check(request);
        fieldAuth.authenticate(token);
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("NO_FILE", "사진 파일 필수");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw ApiException.badRequest("NOT_IMAGE", "이미지 파일만 가능합니다");
        }
        String key = storage.store(file);
        return Map.of("photo_key", key, "content_type", ct, "size", file.getSize());
    }

    /** 본인 프로필 사진 — has_photo=true 일 때만. */
    @GetMapping("/my-photo")
    public ResponseEntity<Resource> myPhoto(@RequestHeader("X-Field-Token") String token, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        if (p.getPhotoKey() == null) throw ApiException.notFound("NO_PHOTO", "프로필 사진 없음");
        Resource res = storage.load(p.getPhotoKey());
        String ct = p.getPhotoContentType() != null ? p.getPhotoContentType() : MediaType.IMAGE_JPEG_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, ct)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(res);
    }

    /** 사진 다운로드 — 본인 출/퇴근 사진만 조회 가능. */
    @GetMapping("/photo")
    public ResponseEntity<Resource> photo(@RequestHeader("X-Field-Token") String token,
                                          @RequestParam("key") String key, HttpServletRequest request) {
        rateLimiter.check(request);
        Person p = fieldAuth.authenticate(token);
        boolean owns = attRepo.existsByPersonIdAndCheckInPhotoKey(p.getId(), key)
                || attRepo.existsByPersonIdAndCheckOutPhotoKey(p.getId(), key);
        if (!owns) throw ApiException.forbidden("PHOTO_FORBIDDEN", "본인 사진만 조회 가능합니다");
        Resource res = storage.load(key);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .body(res);
    }

    private Map<String, Object> personMap(Person p) {
        Map<String, Object> m = new HashMap<>();
        m.put("token", p.getAttendanceCode());
        m.put("person_id", p.getId());
        m.put("name", p.getName());
        m.put("job_title", p.getJobTitle());
        m.put("supplier_id", p.getSupplierId());
        m.put("supplier_name", p.getSupplierId() != null
                ? companies.findById(p.getSupplierId()).map(Company::getName).orElse(null)
                : null);
        m.put("has_photo", p.getPhotoKey() != null);
        return m;
    }

    private List<Map<String, Object>> listActiveWorkPlans(Long personId) {
        var statuses = List.of(WorkPlanStatus.SUBMITTED, WorkPlanStatus.APPROVED,
                WorkPlanStatus.IN_PROGRESS, WorkPlanStatus.DONE);
        var wpps = wppRepo.findByPersonId(personId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkPlanPerson wpp : wpps) {
            WorkPlan wp = wpRepo.findById(wpp.getWorkPlanId()).orElse(null);
            if (wp == null || !statuses.contains(wp.getStatus())) continue;
            Site site = wp.getSiteId() != null ? sites.findById(wp.getSiteId()).orElse(null) : null;
            var open = attRepo.findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNullOrderByIdDesc(personId, wp.getId());
            Map<String, Object> m = new HashMap<>();
            m.put("work_plan_id", wp.getId());
            m.put("title", wp.getTitle());
            m.put("work_date", wp.getWorkDate());
            m.put("start_time", wp.getStartTime());
            m.put("end_time", wp.getEndTime());
            m.put("site_name", site != null ? site.getName() : null);
            m.put("site_address", site != null ? site.getAddress() : null);
            m.put("site_lat", site != null ? site.getLatitude() : null);
            m.put("site_lng", site != null ? site.getLongitude() : null);
            m.put("site_radius_m", site != null ? site.getGeofenceRadiusM() : null);
            m.put("open_session_id", open.map(AttendanceSession::getId).orElse(null));
            m.put("check_in_at", open.map(AttendanceSession::getCheckInAt).orElse(null));
            m.put("break_start_at", open.map(AttendanceSession::getBreakStartAt).orElse(null));
            m.put("break_minutes", open.map(AttendanceSession::getBreakMinutes).orElse(0));
            // 오늘 이미 끝난(퇴근까지 완료된) 마지막 세션 — 앱이 "오늘 완료" 표시용
            if (open.isEmpty()) {
                java.time.LocalDate today = java.time.LocalDate.now();
                AttendanceSession lastClosed = attRepo
                        .findFirstByPersonIdAndWorkPlanIdAndCheckOutAtIsNotNullOrderByCheckInAtDesc(personId, wp.getId())
                        .filter(s -> s.getCheckInAt().toLocalDate().equals(today))
                        .orElse(null);
                if (lastClosed != null) {
                    Map<String, Object> closed = new HashMap<>();
                    closed.put("id", lastClosed.getId());
                    closed.put("check_in_at", lastClosed.getCheckInAt());
                    closed.put("check_out_at", lastClosed.getCheckOutAt());
                    closed.put("hours", java.time.Duration.between(
                            lastClosed.getCheckInAt(), lastClosed.getCheckOutAt()).toMinutes() / 60.0);
                    m.put("today_closed_session", closed);
                }
            }
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> sessionMap(AttendanceSession s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("person_id", s.getPersonId());
        m.put("work_plan_id", s.getWorkPlanId());
        m.put("check_in_at", s.getCheckInAt());
        m.put("check_out_at", s.getCheckOutAt());
        m.put("hours", s.getCheckOutAt() != null
                ? java.time.Duration.between(s.getCheckInAt(), s.getCheckOutAt()).toMinutes() / 60.0
                : null);
        return m;
    }

    public static class AuthRequest { public String code; }
    public static class LoginRequest { public String username; public String password; }
    public static class IssueReportRequest {
        public Long workPlanId;
        public String category;  // PERSON | EQUIPMENT | ETC
        public String message;
    }
    public static class RegisterTokenRequest { public String fcmToken; }
    public static class AnnounceTestRequest { public String title; public String body; }
    public static class SignWcRequest {
        public Double totalHours;
        public String remarks;
        public String signaturePngBase64;
    }
    public static class CheckInRequest {
        public Long workPlanId;
        public Long photoDocId;
        public String photoKey;
        public Double lat;
        public Double lng;
        public String method;  // CODE | BIOMETRIC | NFC (생체검증은 단말)
    }
    public static class CheckOutRequest {
        public Long workPlanId;
        public Long photoDocId;
        public String photoKey;
        public Double lat;
        public Double lng;
    }
}
