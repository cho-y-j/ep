package com.skep.dailywork;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.contract.Contract;
import com.skep.contract.ContractRepository;
import com.skep.contract.RateType;
import com.skep.dailywork.dto.DailyWorkLogResponse;
import com.skep.dailywork.dto.SaveDailyWorkLogRequest;
import com.skep.dailywork.dto.WorkLogLedgerResponse;
import com.skep.dailywork.dto.WorkerCalendarResponse;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.settlement.SettlementCalculator;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;

/**
 * 일일 작업확인서(§3.6.3). 공급사 생성/수정, BP 인앱 서명 or 전표 사진 갈음(단독모드).
 * 월간 원장은 정산주기(현장정산일 26~25) 내 뷰 + 계약 단가 합계.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DailyWorkLogService {

    private final DailyWorkLogRepository repo;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final ContractRepository contracts;
    private final FileStorage storage;
    private final NotificationService notifications;
    private final AuditLogService auditLog;

    // ── 생성/수정 ─────────────────────────────────────────────

    public DailyWorkLogResponse create(SaveDailyWorkLogRequest req, AuthenticatedUser actor) {
        Long supplierId = requireSupplier(actor);
        if (req.equipmentId() == null && req.personId() == null) {
            throw ApiException.badRequest("NO_RESOURCE", "장비 또는 작업자(운전원)를 하나 이상 선택하세요");
        }
        validateOwnership(req, supplierId);
        // 같은 자원·같은 날 중복 방지(부분 유니크 대신 서비스 레벨).
        if (req.equipmentId() != null
                && repo.existsBySupplierCompanyIdAndWorkDateAndEquipmentId(supplierId, req.workDate(), req.equipmentId())) {
            throw ApiException.conflict("DUPLICATE_LOG", "같은 날짜에 이 장비의 일일 확인서가 이미 있습니다");
        }
        if (req.personId() != null
                && repo.existsBySupplierCompanyIdAndWorkDateAndPersonId(supplierId, req.workDate(), req.personId())) {
            throw ApiException.conflict("DUPLICATE_LOG", "같은 날짜에 이 작업자의 일일 확인서가 이미 있습니다");
        }
        DailyWorkLog l = DailyWorkLog.create(supplierId, actor.id());
        apply(l, req, supplierId);
        repo.save(l);
        auditLog.record(actor, AuditAction.DAILY_WORK_LOG_CREATED, AuditTargetType.DAILY_WORK_LOG,
                l.getId(), supplierId, l.getSiteId(), null,
                "{\"work_date\":\"" + l.getWorkDate() + "\"}");
        return toResponse(l);
    }

    public DailyWorkLogResponse update(Long id, SaveDailyWorkLogRequest req, AuthenticatedUser actor) {
        Long supplierId = requireSupplier(actor);
        DailyWorkLog l = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("LOG_NOT_FOUND", "일일 확인서를 찾을 수 없습니다"));
        if (!l.getSupplierCompanyId().equals(supplierId)) {
            throw ApiException.forbidden("DENIED", "본인 회사 확인서만 수정할 수 있습니다");
        }
        if (l.getSignStatus() == WorkLogSignStatus.SIGNED) {
            throw ApiException.badRequest("ALREADY_SIGNED", "서명된 확인서는 수정할 수 없습니다");
        }
        validateOwnership(req, supplierId);
        apply(l, req, supplierId);
        auditLog.record(actor, AuditAction.DAILY_WORK_LOG_UPDATED, AuditTargetType.DAILY_WORK_LOG,
                l.getId(), supplierId, l.getSiteId(), null,
                "{\"work_date\":\"" + l.getWorkDate() + "\"}");
        return toResponse(l);
    }

    private void apply(DailyWorkLog l, SaveDailyWorkLogRequest req, Long supplierId) {
        // 계약 연결 → 단가/구분/BP 상속 원천.
        Contract contract = null;
        if (req.contractId() != null) {
            contract = contracts.findById(req.contractId()).orElseThrow(() ->
                    ApiException.badRequest("CONTRACT_NOT_FOUND", "선택한 계약을 찾을 수 없습니다"));
            if (!contract.getSupplierCompanyId().equals(supplierId)) {
                throw ApiException.badRequest("CONTRACT_DENIED", "본인 회사 계약만 연결할 수 있습니다");
            }
        }
        l.setContractId(req.contractId());
        l.setEquipmentId(req.equipmentId());
        l.setPersonId(req.personId());
        l.setWorkDate(req.workDate());
        l.setWorkContent(trimToNull(req.workContent()));
        l.setWorkLocation(trimToNull(req.workLocation()));
        // 구분: 명시값 우선, 없으면 계약 상속, 그래도 없으면 일대.
        RateType rt = req.rateType() != null ? req.rateType()
                : (contract != null ? contract.getRateType() : RateType.DAILY);
        l.setRateType(rt);
        // BP: 명시값 우선, 없으면 계약 상속.
        Long bpId = req.bpCompanyId() != null ? req.bpCompanyId()
                : (contract != null ? contract.getBpCompanyId() : null);
        if (bpId != null) validateBp(bpId);
        l.setBpCompanyId(bpId);
        // 현장: 지정 시 이름 자동 채움, 미지정이면 텍스트 폴백.
        l.setSiteId(req.siteId());
        if (req.siteId() != null) {
            l.setSiteName(sites.findById(req.siteId()).map(Site::getName).orElse(trimToNull(req.siteName())));
        } else {
            l.setSiteName(trimToNull(req.siteName()));
        }
        l.setOtEarly(otVal(req.otEarly()));
        l.setOtLunch(otVal(req.otLunch()));
        l.setOtEvening(otVal(req.otEvening()));
        l.setOtNight(otVal(req.otNight()));
        l.setOtOvernight(otVal(req.otOvernight()));
        l.setStartTime(req.startTime());
        l.setEndTime(req.endTime());
        l.setMemo(trimToNull(req.memo()));
        l.touch();
    }

    // ── 목록 ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DailyWorkLogResponse> list(AuthenticatedUser actor, LocalDate from, LocalDate to,
                                           Long equipmentId, Long personId) {
        List<DailyWorkLog> rows;
        if (actor.role() == Role.ADMIN) {
            rows = repo.findAll().stream()
                    .sorted((a, b) -> {
                        int c = b.getWorkDate().compareTo(a.getWorkDate());
                        return c != 0 ? c : Long.compare(b.getId(), a.getId());
                    }).toList();
        } else if (actor.role() == Role.BP) {
            rows = actor.companyId() == null ? List.of()
                    : repo.findByBpCompanyIdOrderByWorkDateDescIdDesc(actor.companyId());
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            rows = actor.companyId() == null ? List.of()
                    : repo.findBySupplierCompanyIdOrderByWorkDateDescIdDesc(actor.companyId());
        } else {
            rows = List.of();
        }
        return rows.stream()
                .filter(l -> from == null || !l.getWorkDate().isBefore(from))
                .filter(l -> to == null || !l.getWorkDate().isAfter(to))
                .filter(l -> equipmentId == null || equipmentId.equals(l.getEquipmentId()))
                .filter(l -> personId == null || personId.equals(l.getPersonId()))
                .map(this::toResponse).toList();
    }

    // ── 전표 사진(단독모드 갈음) ──────────────────────────────

    public DailyWorkLogResponse uploadPhoto(Long id, MultipartFile file, AuthenticatedUser actor) {
        Long supplierId = requireSupplier(actor);
        DailyWorkLog l = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("LOG_NOT_FOUND", "일일 확인서를 찾을 수 없습니다"));
        if (!l.getSupplierCompanyId().equals(supplierId)) {
            throw ApiException.forbidden("DENIED", "본인 회사 확인서만 수정할 수 있습니다");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.toLowerCase().startsWith("image/")) {
            throw ApiException.badRequest("NOT_IMAGE", "이미지 파일만 업로드할 수 있습니다");
        }
        String oldKey = l.getSlipPhotoKey();
        String key = storage.store(file);
        l.setSlipPhotoKey(key);
        // 사진 갈음 — BP 서명이 없으면 사진(PHOTO) 확정.
        if (l.getSignStatus() != WorkLogSignStatus.SIGNED) {
            l.setSignStatus(WorkLogSignStatus.PHOTO);
        }
        l.touch();
        if (oldKey != null) storage.delete(oldKey);
        return toResponse(l);
    }

    @Transactional(readOnly = true)
    public Resource loadPhoto(Long id, AuthenticatedUser actor) {
        DailyWorkLog l = getForRead(id, actor);
        if (l.getSlipPhotoKey() == null) {
            throw ApiException.notFound("NO_PHOTO", "첨부된 전표 사진이 없습니다");
        }
        return storage.load(l.getSlipPhotoKey());
    }

    // ── BP 서명 ──────────────────────────────────────────────

    public DailyWorkLogResponse sign(Long id, String pngBase64, AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 서명할 수 있습니다");
        }
        DailyWorkLog l = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("LOG_NOT_FOUND", "일일 확인서를 찾을 수 없습니다"));
        if (actor.role() == Role.BP
                && (l.getBpCompanyId() == null || !l.getBpCompanyId().equals(actor.companyId()))) {
            throw ApiException.forbidden("DENIED", "본인 회사 앞 확인서만 서명할 수 있습니다");
        }
        byte[] png = decodePng(pngBase64);
        l.setSignImage(png);
        l.setSignStatus(WorkLogSignStatus.SIGNED);
        l.setBpSignedBy(actor.id());
        l.setBpSignedAt(java.time.LocalDateTime.now());
        l.touch();
        notifications.sendToCompany(l.getSupplierCompanyId(),
                "DAILY_WORK_LOG_SIGNED", "일일 확인서 서명 완료",
                l.getWorkDate() + " " + resourceLabel(l) + " — BP 확인 서명",
                "DAILY_WORK_LOG", l.getId(), l.getSiteId(), notifications.senderLabelOf(actor));
        auditLog.record(actor, AuditAction.DAILY_WORK_LOG_SIGNED, AuditTargetType.DAILY_WORK_LOG,
                l.getId(), l.getSupplierCompanyId(), l.getSiteId(), null,
                "{\"work_date\":\"" + l.getWorkDate() + "\"}");
        return toResponse(l);
    }

    @Transactional(readOnly = true)
    public byte[] loadSignImage(Long id, AuthenticatedUser actor) {
        DailyWorkLog l = getForRead(id, actor);
        if (l.getSignImage() == null) {
            throw ApiException.notFound("NO_SIGN_IMAGE", "서명 이미지가 없습니다");
        }
        return l.getSignImage();
    }

    // ── 월간 원장 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public WorkLogLedgerResponse ledger(AuthenticatedUser actor, Long siteId, String siteName,
                                        Long equipmentId, Long personId, String period) {
        if (equipmentId == null && personId == null) {
            throw ApiException.badRequest("NO_RESOURCE", "장비 또는 작업자를 지정하세요");
        }
        YearMonth ym = parseMonth(period);

        // 현장정산일 → 정산주기.
        Site site = siteId != null ? sites.findById(siteId).orElse(null) : null;
        Integer settlementDay = site != null ? site.getSettlementDay() : null;
        LocalDate[] range = settlementPeriod(ym, settlementDay);
        LocalDate start = range[0], end = range[1];

        List<DailyWorkLog> rows = equipmentId != null
                ? repo.findByEquipmentIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(equipmentId, start, end)
                : repo.findByPersonIdAndWorkDateBetweenOrderByWorkDateAscIdAsc(personId, start, end);
        // 스코프 격리 — 공급사=본인, BP=자기 앞.
        rows = rows.stream().filter(l -> canRead(l, actor)).toList();

        // 계약 단가 원천 — 행들 중 첫 계약.
        Contract contract = rows.stream().map(DailyWorkLog::getContractId)
                .filter(java.util.Objects::nonNull).findFirst()
                .flatMap(contracts::findById).orElse(null);

        RateType rateType = contract != null ? contract.getRateType()
                : rows.stream().map(DailyWorkLog::getRateType).findFirst().orElse(RateType.DAILY);

        String resolvedSiteName = site != null ? site.getName()
                : (siteName != null ? siteName
                : rows.stream().map(DailyWorkLog::getSiteName).filter(java.util.Objects::nonNull).findFirst().orElse(null));

        // 합계.
        BigDecimal earlyH = BigDecimal.ZERO, lunchH = BigDecimal.ZERO, eveningH = BigDecimal.ZERO,
                nightH = BigDecimal.ZERO, overnightH = BigDecimal.ZERO;
        List<WorkLogLedgerResponse.Row> rowDtos = new java.util.ArrayList<>();
        for (DailyWorkLog l : rows) {
            earlyH = earlyH.add(l.getOtEarly());
            lunchH = lunchH.add(l.getOtLunch());
            eveningH = eveningH.add(l.getOtEvening());
            nightH = nightH.add(l.getOtNight());
            overnightH = overnightH.add(l.getOtOvernight());
            rowDtos.add(new WorkLogLedgerResponse.Row(
                    l.getId(), l.getWorkDate(), l.getWorkContent(),
                    l.getOtEarly(), l.getOtLunch(), l.getOtEvening(), l.getOtNight(), l.getOtOvernight(),
                    l.getSignStatus().name(), l.getMemo()));
        }
        int workDays = rows.size();

        Long baseRate = contract != null ? contract.getBaseRate() : null;
        WorkLogLedgerResponse.Rates otRates = contract != null
                ? new WorkLogLedgerResponse.Rates(contract.getRateEarly(), contract.getRateLunch(),
                contract.getRateEvening(), contract.getRateNight(), contract.getRateOvernight())
                : null;

        Long baseAmount = null, otAmount = null, totalAmount = null;
        if (contract != null && baseRate != null) {
            baseAmount = rateType == RateType.MONTHLY
                    ? Math.round(baseRate / (double) SettlementCalculator.WORKDAYS_PER_MONTH * workDays)
                    : baseRate * (long) workDays;
            long ot = money(earlyH, contract.getRateEarly())
                    + money(lunchH, contract.getRateLunch())
                    + money(eveningH, contract.getRateEvening())
                    + money(nightH, contract.getRateNight())
                    + money(overnightH, contract.getRateOvernight());
            otAmount = ot;
            totalAmount = baseAmount + ot;
        }

        return new WorkLogLedgerResponse(
                ym.toString(), start, end, settlementDay,
                siteId, resolvedSiteName,
                equipmentId, equipmentId != null ? equipmentLabel(equipmentId) : null,
                personId, personId != null ? personName(personId) : null,
                contract != null ? contract.getId() : null,
                rateType, baseRate, otRates,
                rowDtos,
                new WorkLogLedgerResponse.Totals(workDays, earlyH, lunchH, eveningH, nightH, overnightH,
                        baseAmount, otAmount, totalAmount));
    }

    /** 작업자(Person) 본인 일일 확인서 — field-auth 컨트롤러에서 호출. */
    @Transactional(readOnly = true)
    public List<DailyWorkLog> listForPerson(Long personId) {
        return repo.findByPersonIdOrderByWorkDateDescIdDesc(personId);
    }

    /** 작업자 본인 "내 작업 달력"(§0-A #3) — 정산주기(현장정산일 26~25) 창 + 근무일 + 합계.
     *  period(YYYY-MM) 미지정 시 오늘이 속한 주기. settlementDay 는 인원 로그의 현장에서 해석. */
    @Transactional(readOnly = true)
    public WorkerCalendarResponse personCalendar(Long personId, String period) {
        List<DailyWorkLog> all = repo.findByPersonIdOrderByWorkDateDescIdDesc(personId);
        Integer settlementDay = resolveSettlementDay(all);
        YearMonth ym = (period == null || period.isBlank())
                ? anchorForToday(settlementDay)
                : parseMonth(period);
        LocalDate[] range = settlementPeriod(ym, settlementDay);
        LocalDate start = range[0], end = range[1];

        BigDecimal earlyH = BigDecimal.ZERO, lunchH = BigDecimal.ZERO, eveningH = BigDecimal.ZERO,
                nightH = BigDecimal.ZERO, overnightH = BigDecimal.ZERO, otTotalH = BigDecimal.ZERO;
        int signed = 0, photo = 0, unsigned = 0;
        List<WorkerCalendarResponse.Day> days = new java.util.ArrayList<>();
        // 주기 내 로그만(오름차순 — 달력 표시 순).
        List<DailyWorkLog> inCycle = all.stream()
                .filter(l -> !l.getWorkDate().isBefore(start) && !l.getWorkDate().isAfter(end))
                .sorted(java.util.Comparator.comparing(DailyWorkLog::getWorkDate)
                        .thenComparing(DailyWorkLog::getId))
                .toList();
        for (DailyWorkLog l : inCycle) {
            BigDecimal ot = l.getOtEarly().add(l.getOtLunch()).add(l.getOtEvening())
                    .add(l.getOtNight()).add(l.getOtOvernight());
            earlyH = earlyH.add(l.getOtEarly());
            lunchH = lunchH.add(l.getOtLunch());
            eveningH = eveningH.add(l.getOtEvening());
            nightH = nightH.add(l.getOtNight());
            overnightH = overnightH.add(l.getOtOvernight());
            otTotalH = otTotalH.add(ot);
            switch (l.getSignStatus()) {
                case SIGNED -> signed++;
                case PHOTO -> photo++;
                default -> unsigned++;
            }
            days.add(new WorkerCalendarResponse.Day(
                    l.getId(), l.getWorkDate(), l.getWorkContent(), l.getWorkLocation(),
                    l.getSiteName(), l.getRateType(),
                    l.getOtEarly(), l.getOtLunch(), l.getOtEvening(), l.getOtNight(), l.getOtOvernight(), ot,
                    l.getStartTime(), l.getEndTime(),
                    l.getSignStatus().name(), l.getMemo()));
        }
        return new WorkerCalendarResponse(ym.toString(), start, end, settlementDay, days,
                new WorkerCalendarResponse.Totals(days.size(),
                        earlyH, lunchH, eveningH, nightH, overnightH, otTotalH,
                        signed, photo, unsigned));
    }

    /** 인원 로그의 현장 중 현장정산일이 설정된 가장 최근 현장의 값(없으면 null → 달력월). */
    private Integer resolveSettlementDay(List<DailyWorkLog> logs) {
        List<Long> siteIds = logs.stream().map(DailyWorkLog::getSiteId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (siteIds.isEmpty()) return null;
        java.util.Map<Long, Integer> dayBySite = new java.util.HashMap<>();
        sites.findAllById(siteIds).forEach(s -> dayBySite.put(s.getId(), s.getSettlementDay()));
        for (DailyWorkLog l : logs) { // desc → 최근 현장 우선
            if (l.getSiteId() != null) {
                Integer d = dayBySite.get(l.getSiteId());
                if (d != null) return d;
            }
        }
        return null;
    }

    /** period 미지정 시 오늘이 속한 정산주기의 앵커 기준월. */
    private static YearMonth anchorForToday(Integer settlementDay) {
        LocalDate today = LocalDate.now();
        if (settlementDay == null) return YearMonth.from(today);
        return today.getDayOfMonth() > settlementDay
                ? YearMonth.from(today).plusMonths(1)
                : YearMonth.from(today);
    }

    // ── helpers ──────────────────────────────────────────────

    private Long requireSupplier(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 일일 확인서를 작성할 수 있습니다");
        }
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        return actor.companyId();
    }

    private void validateOwnership(SaveDailyWorkLogRequest req, Long supplierId) {
        if (req.equipmentId() != null) {
            Equipment e = equipmentRepo.findById(req.equipmentId()).orElseThrow(() ->
                    ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
            if (!supplierId.equals(e.getSupplierId())) {
                throw ApiException.forbidden("DENIED", "본인 회사 장비만 선택할 수 있습니다");
            }
        }
        if (req.personId() != null) {
            Person p = personRepo.findById(req.personId()).orElseThrow(() ->
                    ApiException.badRequest("PERSON_NOT_FOUND", "작업자를 찾을 수 없습니다"));
            if (!supplierId.equals(p.getSupplierId())) {
                throw ApiException.forbidden("DENIED", "본인 회사 작업자만 선택할 수 있습니다");
            }
        }
    }

    private void validateBp(Long bpCompanyId) {
        Company bp = companies.findById(bpCompanyId).orElseThrow(() ->
                ApiException.badRequest("BP_NOT_FOUND", "선택한 BP사를 찾을 수 없습니다"));
        if (bp.getType() != CompanyType.BP) {
            throw ApiException.badRequest("NOT_BP_COMPANY", "BP사가 아닌 회사는 선택할 수 없습니다");
        }
    }

    private DailyWorkLog getForRead(Long id, AuthenticatedUser actor) {
        DailyWorkLog l = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("LOG_NOT_FOUND", "일일 확인서를 찾을 수 없습니다"));
        if (!canRead(l, actor)) throw ApiException.forbidden("DENIED", "조회 권한이 없습니다");
        return l;
    }

    private boolean canRead(DailyWorkLog l, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return true;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER))
            return l.getSupplierCompanyId().equals(actor.companyId());
        if (actor.role() == Role.BP)
            return actor.companyId() != null && actor.companyId().equals(l.getBpCompanyId());
        return false;
    }

    private byte[] decodePng(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw ApiException.badRequest("NO_SIGNATURE", "서명 이미지가 필요합니다");
        }
        String raw = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_SIGNATURE", "서명 이미지 base64 디코딩 실패");
        }
    }

    private static long money(BigDecimal hours, Long rate) {
        if (rate == null || hours == null || hours.signum() == 0) return 0L;
        return hours.multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal otVal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** 정산주기 [start,end] — 현장정산일 있으면 "전월(day+1)~당월 day", 없으면 달력월. */
    private static LocalDate[] settlementPeriod(YearMonth ym, Integer settlementDay) {
        if (settlementDay == null) {
            return new LocalDate[]{ ym.atDay(1), ym.atEndOfMonth() };
        }
        LocalDate end = ym.atDay(Math.min(settlementDay, ym.lengthOfMonth()));
        YearMonth prev = ym.minusMonths(1);
        LocalDate prevEnd = prev.atDay(Math.min(settlementDay, prev.lengthOfMonth()));
        return new LocalDate[]{ prevEnd.plusDays(1), end };
    }

    private static YearMonth parseMonth(String period) {
        if (period == null || period.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("BAD_PERIOD", "period 는 YYYY-MM 형식이어야 합니다");
        }
    }

    private DailyWorkLogResponse toResponse(DailyWorkLog l) {
        return DailyWorkLogResponse.from(l,
                companyName(l.getSupplierCompanyId()),
                l.getBpCompanyId() != null ? companyName(l.getBpCompanyId()) : null,
                l.getEquipmentId() != null ? equipmentLabel(l.getEquipmentId()) : null,
                l.getPersonId() != null ? personName(l.getPersonId()) : null);
    }

    private String resourceLabel(DailyWorkLog l) {
        if (l.getEquipmentId() != null) return equipmentLabel(l.getEquipmentId());
        if (l.getPersonId() != null) return personName(l.getPersonId());
        return "#" + l.getId();
    }

    private String companyName(Long id) {
        if (id == null) return null;
        return companies.findById(id).map(Company::getName).orElse(null);
    }

    private String equipmentLabel(Long id) {
        return equipmentRepo.findById(id)
                .map(e -> e.getVehicleNo() != null ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel() : "장비 #" + id))
                .orElse("장비 #" + id);
    }

    private String personName(Long id) {
        return personRepo.findById(id).map(Person::getName).orElse("작업자 #" + id);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
