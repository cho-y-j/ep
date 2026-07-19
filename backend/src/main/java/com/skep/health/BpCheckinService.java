package com.skep.health;

import com.skep.common.ApiException;
import com.skep.health.dto.BpCheckinResponse;
import com.skep.health.dto.BpThresholdsPayload;
import com.skep.health.dto.CreateBpCheckinRequest;
import com.skep.health.dto.SitePersonResponse;
import com.skep.health.dto.UnmeasuredPersonResponse;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.HealthRiskLevel;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.safety.SiteSafetySettings;
import com.skep.safety.SiteSafetySettingsRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * P5-W4 1겹 — 혈압 체크인. 관리자·BP·공급사 대행 입력 + 작업자 본인(field-token). verdict 서버 계산.
 * BLOCK 이어도 출근 하드차단 없음(권고+관리자 통보+기록). 스코프: BP=자기 현장, 공급사=본인 인원.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BpCheckinService {

    private static final int SYS_MIN = 50, SYS_MAX = 300;
    private static final int DIA_MIN = 30, DIA_MAX = 200;

    private final BpCheckinRepository repo;
    private final PersonRepository persons;
    private final SiteRepository sites;
    private final SiteSafetySettingsRepository safetySettings;
    private final NotificationService notifications;

    // ── 입력 ──────────────────────────────────────────────────

    /** 관리자·BP·공급사 대행 입력. BP=현장 소유 검증, 공급사=본인 인원 검증. */
    public BpCheckinResponse create(CreateBpCheckinRequest req, AuthenticatedUser actor) {
        if (req.personId() == null) throw ApiException.badRequest("NO_PERSON", "작업자를 선택하세요");
        Person p = persons.findById(req.personId()).orElseThrow(() ->
                ApiException.badRequest("PERSON_NOT_FOUND", "작업자를 찾을 수 없습니다"));

        Long siteId = req.siteId();
        switch (actor.role()) {
            case ADMIN -> { /* 전체 */ }
            case BP -> {
                if (siteId == null) throw ApiException.badRequest("NO_SITE", "현장을 선택하세요");
                requireBpSite(siteId, actor);
                if (!Objects.equals(p.getCurrentSiteId(), siteId)) {
                    throw ApiException.badRequest("PERSON_NOT_AT_SITE", "해당 현장에 배치된 작업자가 아닙니다");
                }
            }
            case EQUIPMENT_SUPPLIER, MANPOWER_SUPPLIER -> {
                if (!Objects.equals(p.getSupplierId(), actor.companyId())) {
                    throw ApiException.forbidden("DENIED", "본인 회사 작업자만 입력할 수 있습니다");
                }
                if (siteId == null) siteId = p.getCurrentSiteId();   // 미지정 시 현재 배치 현장.
            }
            default -> throw ApiException.forbidden("DENIED", "혈압 체크인 권한이 없습니다");
        }

        Long bpCompanyId = siteId != null
                ? sites.findById(siteId).map(Site::getBpCompanyId).orElse(null) : null;
        BpCheckin c = record(p, siteId, bpCompanyId, req.sys(), req.dia(), req.pulse(),
                req.method(), req.measuredAt(), actor.id());
        return toResponse(c, p);
    }

    /** 작업자 본인(field-token) 셀프 체크인 — 컨트롤러가 person·site 컨텍스트를 해석해 넘김. */
    public BpCheckin recordSelf(Person p, Long siteId, Long bpCompanyId,
                                Integer sys, Integer dia, Integer pulse, String method) {
        return record(p, siteId, bpCompanyId, sys, dia, pulse, method, null, null);
    }

    /** 공통 기록 — verdict 계산·저장·BLOCK 관리자 통보(증거사슬). */
    private BpCheckin record(Person p, Long siteId, Long bpCompanyId,
                             Integer sys, Integer dia, Integer pulse, String method,
                             LocalDateTime measuredAt, Long createdBy) {
        if (sys == null || dia == null) throw ApiException.badRequest("NO_BP", "수축기·이완기 혈압을 입력하세요");
        if (sys < SYS_MIN || sys > SYS_MAX || dia < DIA_MIN || dia > DIA_MAX || dia >= sys) {
            throw ApiException.badRequest("BP_OUT_OF_RANGE", "혈압 값이 올바르지 않습니다");
        }
        BpVerdict verdict = BpVerdict.evaluate(sys, dia, thresholdsFor(siteId));

        BpCheckin c = new BpCheckin();
        c.setPersonId(p.getId());
        c.setSiteId(siteId);
        c.setSys(sys);
        c.setDia(dia);
        c.setPulse(pulse);
        c.setMethod(normalizeMethod(method));
        c.setVerdict(verdict);
        c.setMeasuredAt(measuredAt != null ? measuredAt : LocalDateTime.now());
        c.setCreatedBy(createdBy);
        repo.save(c);

        if (verdict == BpVerdict.BLOCK) notifyBlock(c, p, bpCompanyId);
        return c;
    }

    /** BLOCK 통보 1회 — BP·공급사 관리자에게(출근 차단이 아니라 조치 권고). */
    private void notifyBlock(BpCheckin c, Person p, Long bpCompanyId) {
        String who = p.getName();
        String title = "혈압 이상 — " + who;
        String msg = who + " 혈압 " + c.getSys() + "/" + c.getDia()
                + " (차단권고 임계 초과) — 고소·고강도 작업 배제를 권고합니다.";
        Set<Long> companies = new LinkedHashSet<>();
        if (bpCompanyId != null) companies.add(bpCompanyId);
        if (p.getSupplierId() != null) companies.add(p.getSupplierId());
        for (Long comp : companies) {
            notifications.sendToCompany(comp, NotificationType.BP_CHECKIN_BLOCK, title, msg,
                    "SITE", c.getSiteId(), c.getSiteId(), "시스템 (혈압 체크인)");
        }
    }

    // ── 조회 ──────────────────────────────────────────────────

    /** 현장·날짜 체크인 목록. BP=자기 현장, 공급사=본인 인원만. date 미지정 시 오늘. */
    @Transactional(readOnly = true)
    public List<BpCheckinResponse> listBySiteAndDate(Long siteId, LocalDate date, AuthenticatedUser actor) {
        Site site = requireViewableSite(siteId, actor);
        LocalDate d = date != null ? date : LocalDate.now();
        List<BpCheckin> rows = repo.findBySiteIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(
                siteId, d.atStartOfDay(), d.atTime(LocalTime.MAX));
        Map<Long, Person> personMap = personMap(rows.stream().map(BpCheckin::getPersonId).toList());
        boolean supplier = isSupplier(actor);
        return rows.stream()
                .filter(c -> !supplier || ownsPerson(personMap.get(c.getPersonId()), actor))
                .map(c -> toResponse(c, personMap.get(c.getPersonId()), site))
                .toList();
    }

    /** 오늘(또는 지정일) 혈압 미측정 고위험군(HIGH) — 현장 배치 기준. 관제 강조 대상. */
    @Transactional(readOnly = true)
    public List<UnmeasuredPersonResponse> unmeasuredHighRisk(Long siteId, LocalDate date, AuthenticatedUser actor) {
        requireViewableSite(siteId, actor);
        LocalDate d = date != null ? date : LocalDate.now();
        List<Person> highRisk = persons.findByCurrentSiteIdAndHealthRiskLevel(siteId, HealthRiskLevel.HIGH).stream()
                .filter(p -> !isSupplier(actor) || ownsPerson(p, actor))
                .toList();
        if (highRisk.isEmpty()) return List.of();
        Set<Long> measured = repo.findByPersonIdInAndMeasuredAtBetween(
                        highRisk.stream().map(Person::getId).toList(),
                        d.atStartOfDay(), d.atTime(LocalTime.MAX))
                .stream().map(BpCheckin::getPersonId).collect(Collectors.toSet());
        return highRisk.stream()
                .filter(p -> !measured.contains(p.getId()))
                .map(p -> new UnmeasuredPersonResponse(
                        p.getId(), p.getName(), p.getHealthRiskLevel().name(), p.getPhone()))
                .toList();
    }

    /** 현장 배치 인원(작업자 선택 드롭다운) — create 게이트와 동일 기준(current_site_id). BP=자기 현장, 공급사=본인 인원만. */
    @Transactional(readOnly = true)
    public List<SitePersonResponse> sitePersons(Long siteId, AuthenticatedUser actor) {
        requireViewableSite(siteId, actor);
        return persons.findByCurrentSiteIdOrderByNameAsc(siteId).stream()
                .filter(p -> !isSupplier(actor) || ownsPerson(p, actor))
                .map(p -> new SitePersonResponse(p.getId(), p.getName()))
                .toList();
    }

    // ── 현장 혈압 임계(자유 설정) ──────────────────────────────

    @Transactional(readOnly = true)
    public BpThresholdsPayload getThresholds(Long siteId, AuthenticatedUser actor) {
        requireBpSite(siteId, actor);
        return safetySettings.findBySiteId(siteId)
                .map(s -> BpThresholdsPayload.of(siteId, true, BpThresholds.from(s)))
                .orElseGet(() -> BpThresholdsPayload.of(siteId, false, BpThresholds.defaults()));
    }

    public BpThresholdsPayload saveThresholds(Long siteId, BpThresholdsPayload req, AuthenticatedUser actor) {
        requireBpSite(siteId, actor);
        BpThresholds def = BpThresholds.defaults();
        int cautionSys = req.cautionSys() != null ? req.cautionSys() : def.cautionSys();
        int cautionDia = req.cautionDia() != null ? req.cautionDia() : def.cautionDia();
        int blockSys = req.blockSys() != null ? req.blockSys() : def.blockSys();
        int blockDia = req.blockDia() != null ? req.blockDia() : def.blockDia();
        if (cautionSys < 1 || cautionDia < 1 || blockSys < 1 || blockDia < 1
                || blockSys < cautionSys || blockDia < cautionDia) {
            throw ApiException.badRequest("BP_THRESHOLD_INVALID",
                    "차단 임계는 주의 임계 이상이어야 하며 값은 양수여야 합니다");
        }
        SiteSafetySettings s = safetySettings.findBySiteId(siteId)
                .orElseGet(() -> SiteSafetySettings.defaults(siteId));
        s.applyBpThresholds(cautionSys, cautionDia, blockSys, blockDia, actor.id());
        return BpThresholdsPayload.of(siteId, true, BpThresholds.from(safetySettings.save(s)));
    }

    // ── helpers ───────────────────────────────────────────────

    private BpThresholds thresholdsFor(Long siteId) {
        if (siteId == null) return BpThresholds.defaults();
        return BpThresholds.from(safetySettings.findBySiteId(siteId).orElse(null));
    }

    private static String normalizeMethod(String method) {
        String m = method == null ? "" : method.trim().toUpperCase();
        return "BLE".equals(m) ? "BLE" : "MANUAL";
    }

    private static boolean isSupplier(AuthenticatedUser actor) {
        return actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
    }

    private static boolean ownsPerson(Person p, AuthenticatedUser actor) {
        return p != null && Objects.equals(p.getSupplierId(), actor.companyId());
    }

    /** BP=자기 현장, ADMIN=전체. (설정·입력 게이트) */
    private Site requireBpSite(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId).orElseThrow(() ->
                ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        if (actor.role() == Role.ADMIN) return site;
        if (actor.role() == Role.BP && Objects.equals(site.getBpCompanyId(), actor.companyId())) return site;
        throw ApiException.forbidden("SITE_DENIED", "본인 현장만 접근할 수 있습니다");
    }

    /** 목록/미측정 조회 게이트 — ADMIN·BP(자기 현장)·공급사(현장 접근 허용, 인원은 본인만 필터). */
    private Site requireViewableSite(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId).orElseThrow(() ->
                ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        switch (actor.role()) {
            case ADMIN -> { return site; }
            case BP -> {
                if (Objects.equals(site.getBpCompanyId(), actor.companyId())) return site;
                throw ApiException.forbidden("SITE_DENIED", "본인 현장만 조회할 수 있습니다");
            }
            case EQUIPMENT_SUPPLIER, MANPOWER_SUPPLIER -> { return site; }   // 본인 인원만 필터링해 반환.
            default -> throw ApiException.forbidden("SITE_DENIED", "조회 권한이 없습니다");
        }
    }

    private Map<Long, Person> personMap(List<Long> ids) {
        return persons.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));
    }

    private BpCheckinResponse toResponse(BpCheckin c, Person p) {
        Site site = c.getSiteId() != null ? sites.findById(c.getSiteId()).orElse(null) : null;
        return toResponse(c, p, site);
    }

    private BpCheckinResponse toResponse(BpCheckin c, Person p, Site site) {
        String pn = p != null ? p.getName() : ("작업자 #" + c.getPersonId());
        String sn = site != null ? site.getName() : null;
        return BpCheckinResponse.of(c, pn, sn);
    }
}
