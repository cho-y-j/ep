package com.skep.workconfirmation;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import com.skep.workconfirmation.dto.MonthlyWorkConfirmationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkConfirmationService {

    private final WorkConfirmationRepository repo;
    private final WorkPlanRepository workPlanRepo;
    private final com.skep.workplan.WorkPlanPersonRepository wppRepo;
    private final CompanyRepository companyRepo;
    private final PersonRepository personRepo;
    private final com.skep.site.SiteRepository siteRepo;
    private final com.skep.worksheet.WorksheetMailService pdfConverter;

    /** 작업확인서 PDF 생성 — HTML 템플릿 → LibreOffice 변환. 사인 PNG 인라인 embed. */
    @Transactional(readOnly = true)
    public byte[] renderPdf(Long id, AuthenticatedUser actor) {
        WorkConfirmation wc = get(id, actor);
        WorkPlan wp = workPlanOrThrow(wc.getWorkPlanId());
        var site = siteRepo.findById(wp.getSiteId()).orElse(null);
        var bp = companyRepo.findById(wc.getBpCompanyId()).orElse(null);
        var supplier = companyRepo.findById(wc.getIssuingSupplierCompanyId()).orElse(null);
        String html = renderHtml(wc, wp, site, bp, supplier);
        try {
            return pdfConverter.convertHtmlToPdf(html.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "work-confirmation-" + id);
        } catch (Exception e) {
            throw ApiException.badRequest("PDF_RENDER_FAILED", "PDF 변환 실패: " + e.getMessage());
        }
    }

    /** BP 서명 대기 작업확인서 — 공급사 서명완료 + BP 미서명. (BP 앱 서명 목록) 회사 스코프(bp_company_id). */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> listBpPending(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        var pending = repo.findByBpCompanyIdOrderByWorkDateDescIdDesc(actor.companyId()).stream()
                .filter(wc -> wc.getSupplierSignedAt() != null && wc.getBpSignedAt() == null)
                .toList();
        if (pending.isEmpty()) return List.of();
        var personIds = pending.stream().map(WorkConfirmation::getPersonId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        personRepo.findAllById(personIds).forEach(p -> names.put(p.getId(), p.getName()));
        var wpIds = pending.stream().map(WorkConfirmation::getWorkPlanId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> titles = new java.util.HashMap<>();
        workPlanRepo.findAllById(wpIds).forEach(wp -> titles.put(wp.getId(), wp.getTitle()));
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (WorkConfirmation wc : pending) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", wc.getId());
            m.put("work_plan_id", wc.getWorkPlanId());
            m.put("work_date", wc.getWorkDate());
            m.put("total_hours", wc.getTotalHours());
            m.put("person_id", wc.getPersonId());
            m.put("person_name", wc.getPersonId() != null ? names.get(wc.getPersonId()) : null);
            m.put("wp_title", wc.getWorkPlanId() != null ? titles.get(wc.getWorkPlanId()) : null);
            out.add(m);
        }
        return out;
    }

    /** 공급사 발급 작업확인서 목록 — 자기 회사가 발급한 전체(취소 제외), 서명상태 포함. (공급사 앱 조회용) */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> listSupplierList(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            return List.of();
        }
        if (actor.companyId() == null) return List.of();
        var rows = repo.findByIssuingSupplierCompanyIdOrderByWorkDateDescIdDesc(actor.companyId()).stream()
                .filter(wc -> wc.getStatus() != WorkConfirmationStatus.CANCELLED)
                .toList();
        if (rows.isEmpty()) return List.of();
        var personIds = rows.stream().map(WorkConfirmation::getPersonId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        personRepo.findAllById(personIds).forEach(p -> names.put(p.getId(), p.getName()));
        var wpIds = rows.stream().map(WorkConfirmation::getWorkPlanId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> titles = new java.util.HashMap<>();
        workPlanRepo.findAllById(wpIds).forEach(wp -> titles.put(wp.getId(), wp.getTitle()));
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (WorkConfirmation wc : rows) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", wc.getId());
            m.put("work_plan_id", wc.getWorkPlanId());
            m.put("work_date", wc.getWorkDate());
            m.put("total_hours", wc.getTotalHours());
            m.put("person_id", wc.getPersonId());
            m.put("person_name", wc.getPersonId() != null ? names.get(wc.getPersonId()) : null);
            m.put("wp_title", wc.getWorkPlanId() != null ? titles.get(wc.getWorkPlanId()) : null);
            m.put("supplier_signed_at", wc.getSupplierSignedAt());
            m.put("bp_signed_at", wc.getBpSignedAt());
            m.put("status", wc.getStatus());
            out.add(m);
        }
        return out;
    }

    // ───────────────────── 월별 작업확인서 (집계 + PDF) ─────────────────────

    /** 인원 단위 월별 집계. 역할별 스코프: ADMIN 전체 / BP 받은 / 공급사 발급. */
    @Transactional(readOnly = true)
    public List<MonthlyWorkConfirmationResponse> listMonthly(int year, int month, AuthenticatedUser actor) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        java.util.Map<Long, List<WorkConfirmation>> byPerson = new java.util.LinkedHashMap<>();
        for (WorkConfirmation wc : fetchMonthlyScoped(start, end, actor)) {
            if (wc.getStatus() == WorkConfirmationStatus.CANCELLED) continue;
            byPerson.computeIfAbsent(wc.getPersonId(), k -> new java.util.ArrayList<>()).add(wc);
        }
        java.util.Map<Long, String> companyNames = new java.util.HashMap<>();
        List<MonthlyWorkConfirmationResponse> out = new java.util.ArrayList<>();
        for (var e : byPerson.entrySet()) {
            out.add(buildMonthlySummary(e.getKey(), year, month, e.getValue(), companyNames));
        }
        out.sort(java.util.Comparator.comparing(MonthlyWorkConfirmationResponse::personName,
                java.util.Comparator.nullsLast(String::compareTo)));
        return out;
    }

    /** 한 인원의 월별 작업확인서 PDF. */
    @Transactional(readOnly = true)
    public byte[] renderMonthlyPdf(int year, int month, Long personId, AuthenticatedUser actor) {
        if (personId == null) throw ApiException.badRequest("PERSON_REQUIRED", "personId 필수");
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        List<WorkConfirmation> rows = fetchMonthlyScoped(start, end, actor).stream()
                .filter(wc -> wc.getStatus() != WorkConfirmationStatus.CANCELLED)
                .filter(wc -> personId.equals(wc.getPersonId()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (rows.isEmpty()) throw ApiException.notFound("MONTHLY_WC_EMPTY", "해당 월 작업확인서가 없습니다");
        MonthlyWorkConfirmationResponse sum = buildMonthlySummary(personId, year, month, rows, new java.util.HashMap<>());
        try {
            return pdfConverter.convertHtmlToPdf(
                    renderMonthlyHtml(sum).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "monthly-work-confirmation-" + personId + "-" + year + "-" + month);
        } catch (Exception e) {
            throw ApiException.badRequest("PDF_RENDER_FAILED", "PDF 변환 실패: " + e.getMessage());
        }
    }

    private List<WorkConfirmation> fetchMonthlyScoped(LocalDate start, LocalDate end, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return repo.findByWorkDateBetween(start, end);
        if (actor.companyId() == null) return List.of();
        if (actor.role() == Role.BP) {
            return repo.findByBpCompanyIdAndWorkDateBetween(actor.companyId(), start, end);
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            return repo.findByIssuingSupplierCompanyIdAndWorkDateBetween(actor.companyId(), start, end);
        }
        return List.of();
    }

    private MonthlyWorkConfirmationResponse buildMonthlySummary(Long personId, int year, int month,
            List<WorkConfirmation> wcs, java.util.Map<Long, String> companyNames) {
        wcs.sort(java.util.Comparator.comparing(WorkConfirmation::getWorkDate)
                .thenComparing(WorkConfirmation::getId));
        BigDecimal mh = BigDecimal.ZERO, ah = BigDecimal.ZERO, oh = BigDecimal.ZERO,
                nh = BigDecimal.ZERO, th = BigDecimal.ZERO;
        for (WorkConfirmation wc : wcs) {
            mh = mh.add(nz(wc.getMorningHours()));
            ah = ah.add(nz(wc.getAfternoonHours()));
            oh = oh.add(nz(wc.getOvertimeHours()));
            nh = nh.add(nz(wc.getNightHours()));
            th = th.add(nz(wc.getTotalHours()));
        }
        int totalDays = (int) wcs.stream().map(WorkConfirmation::getWorkDate).distinct().count();
        String personName = personRepo.findById(personId).map(Person::getName).orElse("#" + personId);
        Long supplierId = wcs.get(0).getIssuingSupplierCompanyId();
        Long bpId = wcs.get(0).getBpCompanyId();
        String supplierName = companyNames.computeIfAbsent(supplierId,
                id -> companyRepo.findById(id).map(Company::getName).orElse(null));
        String bpName = companyNames.computeIfAbsent(bpId,
                id -> companyRepo.findById(id).map(Company::getName).orElse(null));
        List<MonthlyWorkConfirmationResponse.DailyRow> days = wcs.stream()
                .map(wc -> new MonthlyWorkConfirmationResponse.DailyRow(
                        wc.getId(), wc.getWorkDate(),
                        wc.getMorningHours(), wc.getAfternoonHours(),
                        wc.getOvertimeHours(), wc.getNightHours(), wc.getTotalHours(),
                        wc.getWorkContent(),
                        wc.getSupplierSignedAt() != null, wc.getBpSignedAt() != null))
                .toList();
        return new MonthlyWorkConfirmationResponse(personId, personName, supplierId, supplierName,
                bpId, bpName, year, month, totalDays, mh, ah, oh, nh, th, days);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String renderMonthlyHtml(MonthlyWorkConfirmationResponse m) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>월별 작업 확인서</title>")
                .append("<style>body{font-family:'Noto Sans KR',sans-serif;margin:20px;font-size:12px}")
                .append("h1{text-align:center;font-size:24px}table{width:100%;border-collapse:collapse;margin-bottom:10px}")
                .append("th,td{border:1px solid #999;padding:6px;text-align:center}th{background:#eee}")
                .append("td.l{text-align:left}</style></head><body>")
                .append("<h1>월별 작업 확인서</h1>")
                .append("<table><tr><td style='background:#f4f4f4;width:25%'>대상 월</td><td class='l'>")
                .append(m.year()).append("년 ").append(m.month()).append("월</td></tr>")
                .append("<tr><td style='background:#f4f4f4'>인원</td><td class='l'>").append(esc(m.personName())).append("</td></tr>")
                .append("<tr><td style='background:#f4f4f4'>공급사</td><td class='l'>").append(esc(nb(m.supplierName()))).append("</td></tr>")
                .append("<tr><td style='background:#f4f4f4'>발주사 (BP)</td><td class='l'>").append(esc(nb(m.bpName()))).append("</td></tr>")
                .append("<tr><td style='background:#f4f4f4'>총 근무일</td><td class='l'>").append(m.totalDays()).append("일</td></tr>")
                .append("</table>")
                .append("<table><tr><th>일자</th><th>오전</th><th>오후</th><th>연장</th><th>야간</th><th>일계</th><th>작업내용</th></tr>");
        for (MonthlyWorkConfirmationResponse.DailyRow d : m.days()) {
            sb.append("<tr><td>").append(d.workDate())
              .append("</td><td>").append(h(d.morningHours()))
              .append("</td><td>").append(h(d.afternoonHours()))
              .append("</td><td>").append(h(d.overtimeHours()))
              .append("</td><td>").append(h(d.nightHours()))
              .append("</td><td>").append(h(d.totalHours()))
              .append("</td><td class='l'>").append(esc(d.workContent() == null ? "" : d.workContent())).append("</td></tr>");
        }
        sb.append("<tr style='background:#f4f4f4;font-weight:bold'><td>합계</td><td>")
          .append(h(m.morningHours())).append("</td><td>").append(h(m.afternoonHours()))
          .append("</td><td>").append(h(m.overtimeHours())).append("</td><td>").append(h(m.nightHours()))
          .append("</td><td>").append(h(m.totalHours())).append("</td><td></td></tr>")
          .append("</table></body></html>");
        return sb.toString();
    }

    private static String h(BigDecimal v) {
        return v == null || v.signum() == 0 ? "-" : v.stripTrailingZeros().toPlainString();
    }
    private static String nb(String s) { return s == null || s.isBlank() ? "-" : s; }
    private static String esc(String s) { return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;"); }

    private static String renderHtml(WorkConfirmation wc, WorkPlan wp,
                                      com.skep.site.Site site,
                                      Company bp, Company supplier) {
        String supSig = wc.getSupplierSignaturePng() != null && wc.getSupplierSignaturePng().length > 0
                ? "<img src=\"data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(wc.getSupplierSignaturePng()) + "\" style=\"max-height:60px\"/>"
                : "<span style='color:#999'>미사인</span>";
        String bpSig = wc.getBpSignaturePng() != null && wc.getBpSignaturePng().length > 0
                ? "<img src=\"data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(wc.getBpSignaturePng()) + "\" style=\"max-height:60px\"/>"
                : "<span style='color:#999'>미사인</span>";
        String row = "<tr><td style='border:1px solid #999;padding:6px;background:#f4f4f4;width:25%%'>%s</td><td style='border:1px solid #999;padding:6px'>%s</td></tr>";
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>작업확인서</title>")
                .append("<style>body{font-family:'Noto Sans KR',sans-serif;margin:20px;font-size:12px}")
                .append("h1{text-align:center;font-size:24px}table{width:100%;border-collapse:collapse;margin-bottom:10px}</style>")
                .append("</head><body>")
                .append("<h1>일별 작업 확인서</h1>")
                .append("<table>")
                .append(String.format(row, "작업일자", String.valueOf(wc.getWorkDate())))
                .append(String.format(row, "현장명", site != null ? site.getName() : "-"))
                .append(String.format(row, "발주사 (BP)", bp != null ? bp.getName() : "-"))
                .append(String.format(row, "공급사", (supplier != null ? supplier.getName() : "-") + " (" + wc.getIssuingSupplierType() + ")"))
                .append(String.format(row, "작업내용", wc.getWorkContent() == null ? "" : wc.getWorkContent().replace("\n", "<br/>")))
                .append("</table>")
                .append("<h3>작업시간</h3><table>")
                .append("<tr style='background:#eee'><th style='border:1px solid #999;padding:6px'>구분</th><th style='border:1px solid #999;padding:6px'>시간</th><th style='border:1px solid #999;padding:6px'>시간수</th></tr>")
                .append(timeRow("오전", wc.getMorningTime(), wc.getMorningHours()))
                .append(timeRow("오후", wc.getAfternoonTime(), wc.getAfternoonHours()))
                .append(timeRow("연장", wc.getOvertimeTime(), wc.getOvertimeHours()))
                .append(timeRow("야간", wc.getNightTime(), wc.getNightHours()))
                .append("<tr style='background:#f4f4f4;font-weight:bold'><td style='border:1px solid #999;padding:6px' colspan='2'>합계</td><td style='border:1px solid #999;padding:6px'>")
                .append(wc.getTotalHours() == null ? "-" : wc.getTotalHours().toString()).append("</td></tr>")
                .append("</table>")
                .append("<h3>비고</h3><div style='border:1px solid #999;padding:8px;min-height:40px'>")
                .append(wc.getRemarks() == null ? "" : wc.getRemarks().replace("\n", "<br/>"))
                .append("</div>")
                .append("<table style='margin-top:30px'>")
                .append("<tr><th style='border:1px solid #999;padding:8px;background:#f4f4f4;width:50%'>공급사 사인</th>")
                .append("<th style='border:1px solid #999;padding:8px;background:#f4f4f4'>발주사(BP) 사인</th></tr>")
                .append("<tr><td style='border:1px solid #999;padding:8px;height:90px;vertical-align:middle;text-align:center'>")
                .append(supSig).append("<div style='font-size:11px;margin-top:4px'>")
                .append(wc.getSupplierSignerName() == null ? "" : wc.getSupplierSignerName())
                .append(wc.getSupplierSignedAt() != null ? " (" + wc.getSupplierSignedAt().toLocalDate() + ")" : "")
                .append("</div></td>")
                .append("<td style='border:1px solid #999;padding:8px;height:90px;vertical-align:middle;text-align:center'>")
                .append(bpSig).append("<div style='font-size:11px;margin-top:4px'>")
                .append(wc.getBpSignerName() == null ? "" : wc.getBpSignerName())
                .append(wc.getBpSignedAt() != null ? " (" + wc.getBpSignedAt().toLocalDate() + ")" : "")
                .append("</div></td></tr>")
                .append("</table>")
                .append("</body></html>");
        return sb.toString();
    }

    private static String timeRow(String label, String time, java.math.BigDecimal hours) {
        return "<tr><td style='border:1px solid #999;padding:6px;background:#f4f4f4'>" + label
                + "</td><td style='border:1px solid #999;padding:6px'>" + (time == null ? "" : time)
                + "</td><td style='border:1px solid #999;padding:6px'>" + (hours == null ? "-" : hours.toString())
                + "</td></tr>";
    }

    @Transactional(readOnly = true)
    public List<WorkConfirmation> listByWorkPlan(Long workPlanId, AuthenticatedUser actor) {
        WorkPlan wp = workPlanOrThrow(workPlanId);
        ensureCanView(actor, wp, /*supplierCompanyId*/ null);
        List<WorkConfirmation> all = repo.findByWorkPlanIdOrderByWorkDateDescIdDesc(workPlanId);
        if (actor.role() == Role.ADMIN
                || (actor.role() == Role.BP && actor.companyId() != null
                        && actor.companyId().equals(wp.getBpCompanyId()))) {
            return all;
        }
        // 공급사 본인 회사가 발급한 WC 만 노출 — 같은 작업계획서의 다른 공급사 작업확인서 누설 차단.
        if (actor.companyId() == null) return List.of();
        return all.stream()
                .filter(wc -> actor.companyId().equals(wc.getIssuingSupplierCompanyId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkConfirmation get(Long id, AuthenticatedUser actor) {
        WorkConfirmation wc = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("WORK_CONFIRMATION_NOT_FOUND", "작업확인서를 찾을 수 없습니다"));
        WorkPlan wp = workPlanOrThrow(wc.getWorkPlanId());
        ensureCanView(actor, wp, wc.getIssuingSupplierCompanyId());
        return wc;
    }

    /** 인원(Person) 단위 작업확인서 발급. 동일 (workPlan, person) 1건만 존재. */
    @Transactional
    public WorkConfirmation request(Long workPlanId, Long personId, AuthenticatedUser actor) {
        WorkPlan wp = workPlanOrThrow(workPlanId);
        // 공급사 본인만 발급 가능 (ADMIN 도 허용)
        if (actor.role() != Role.EQUIPMENT_SUPPLIER
                && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("WORK_CONFIRMATION_DENIED",
                    "공급사 또는 ADMIN 만 작업확인서를 발급할 수 있습니다");
        }
        if (actor.companyId() == null && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
        }
        // 작업계획서 상태 가드 — 투입 시작 이후 ~ 완료 까지만 발급 가능
        if (wp.getStatus() != WorkPlanStatus.IN_PROGRESS && wp.getStatus() != WorkPlanStatus.DONE) {
            throw ApiException.badRequest("WP_NOT_IN_PROGRESS",
                    "작업계획서가 투입 시작 이후 상태에서만 작업확인서를 발급할 수 있습니다");
        }
        if (personId == null) {
            throw ApiException.badRequest("PERSON_REQUIRED", "person_id 필수");
        }
        Person p = personRepo.findById(personId)
                .orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND", "인원을 찾을 수 없습니다"));
        // 인원이 작업계획서에 배정되어 있어야 함
        var wpp = wppRepo.findByWorkPlanIdOrderByIdAsc(workPlanId).stream()
                .filter(r -> r.getPersonId().equals(personId))
                .findFirst()
                .orElseThrow(() -> ApiException.forbidden("PERSON_NOT_IN_WP",
                        "이 인원은 해당 작업계획서에 배정되어 있지 않습니다"));
        Long supplierId = wpp.getSupplierCompanyId();
        // 공급사 본인 검증 (ADMIN 제외)
        if (actor.role() != Role.ADMIN) {
            if (actor.companyId() == null || !actor.companyId().equals(supplierId)) {
                throw ApiException.forbidden("NOT_OWN_PERSON",
                        "자기 회사 소속 인원에 대해서만 작업확인서를 발급할 수 있습니다");
            }
        }
        Company supplier = companyRepo.findById(supplierId)
                .orElseThrow(() -> ApiException.badRequest("SUPPLIER_NOT_FOUND", "공급사 회사를 찾을 수 없습니다"));
        if (supplier.getType() != CompanyType.EQUIPMENT && supplier.getType() != CompanyType.MANPOWER) {
            throw ApiException.badRequest("SUPPLIER_TYPE_INVALID",
                    "장비공급사 또는 인력공급사 인원만 발급 가능합니다");
        }

        // 중복 차단: 같은 (workPlan, person) 이미 있으면 그 행 반환 (CANCELLED 면 재오픈)
        var existing = repo.findByWorkPlanIdAndPersonId(workPlanId, personId);
        if (existing.isPresent()) {
            WorkConfirmation wc = existing.get();
            if (wc.getStatus() == WorkConfirmationStatus.CANCELLED) {
                wc.setStatus(WorkConfirmationStatus.PENDING);
                return repo.save(wc);
            }
            return wc;
        }

        WorkConfirmation wc = new WorkConfirmation();
        wc.setWorkPlanId(workPlanId);
        wc.setWorkDate(wp.getWorkDate());
        wc.setPersonId(personId);
        wc.setIssuingSupplierCompanyId(supplierId);
        wc.setIssuingSupplierType(
                supplier.getType() == CompanyType.EQUIPMENT
                        ? IssuingSupplierType.EQUIPMENT
                        : IssuingSupplierType.MANPOWER);
        wc.setBpCompanyId(wp.getBpCompanyId());
        wc.setStatus(WorkConfirmationStatus.PENDING);
        // 공급사측 사이너 후보 — 그 person 본인. 사인 시점에 PNG 받음.
        wc.setSupplierSignerPersonId(personId);
        wc.setSupplierSignerName(p.getName());
        if (actor != null) wc.setCreatedByUserId(actor.id());
        return repo.save(wc);
    }

    /** 작업내용/시간 등 수정. invalidateExisting=true 이면 기존 사인 모두 무효화. */
    @Transactional
    public WorkConfirmation update(Long id, UpdateRequest req, boolean invalidateExisting, AuthenticatedUser actor) {
        WorkConfirmation wc = get(id, actor);
        if (wc.getStatus() == WorkConfirmationStatus.CANCELLED) {
            throw ApiException.badRequest("CANCELLED", "취소된 작업확인서는 수정할 수 없습니다");
        }
        validateHourSlot(req.morningHours, "오전");
        validateHourSlot(req.afternoonHours, "오후");
        validateHourSlot(req.overtimeHours, "연장");
        validateHourSlot(req.nightHours, "철야");
        if (req.workContent != null) wc.setWorkContent(req.workContent);
        if (req.remarks != null) wc.setRemarks(req.remarks);
        if (req.morningTime != null) wc.setMorningTime(req.morningTime);
        if (req.morningHours != null) wc.setMorningHours(req.morningHours);
        if (req.afternoonTime != null) wc.setAfternoonTime(req.afternoonTime);
        if (req.afternoonHours != null) wc.setAfternoonHours(req.afternoonHours);
        if (req.overtimeTime != null) wc.setOvertimeTime(req.overtimeTime);
        if (req.overtimeHours != null) wc.setOvertimeHours(req.overtimeHours);
        if (req.nightTime != null) wc.setNightTime(req.nightTime);
        if (req.nightHours != null) wc.setNightHours(req.nightHours);
        if (req.attendancePhotoDocId != null) wc.setAttendancePhotoDocId(req.attendancePhotoDocId);
        // 슬롯(오전/오후/연장/철야)이 하나라도 입력된 경우에만 합계 재계산.
        // 자동 생성 WC(슬롯 null, totalHours=실근무시간)는 작업내용만 수정 시 totalHours 유지.
        boolean anySlot = wc.getMorningHours() != null || wc.getAfternoonHours() != null
                || wc.getOvertimeHours() != null || wc.getNightHours() != null;
        if (anySlot) recalcTotal(wc);
        if (wc.getTotalHours() != null && wc.getTotalHours().compareTo(new BigDecimal("24")) > 0) {
            throw ApiException.badRequest("HOURS_OVER_24", "시간 합이 24시간을 초과할 수 없습니다");
        }

        boolean anySigned = wc.getSupplierSignedAt() != null || wc.getBpSignedAt() != null;
        if (anySigned && invalidateExisting) {
            wc.setSupplierSignaturePng(null);
            wc.setSupplierSignedAt(null);
            wc.setSupplierSignerName(null);
            wc.setSupplierSignerPersonId(null);
            wc.setSupplierSignerUserId(null);
            wc.setBpSignaturePng(null);
            wc.setBpSignedAt(null);
            wc.setBpSignerName(null);
            wc.setBpSignerUserId(null);
            wc.setStatus(WorkConfirmationStatus.INVALIDATED);
        }
        return repo.save(wc);
    }

    /** 공급사측 사인 — 사이너는 wc.personId 의 인원 본인 (request 시점에 채움). PNG만 받음. */
    @Transactional
    public WorkConfirmation signSupplier(Long id, byte[] png, AuthenticatedUser actor) {
        WorkConfirmation wc = get(id, actor);
        ensureSupplierActor(wc, actor);
        if (wc.getStatus() == WorkConfirmationStatus.CANCELLED) {
            throw ApiException.badRequest("CANCELLED", "취소된 작업확인서");
        }
        wc.setSupplierSignaturePng(png);
        wc.setSupplierSignedAt(LocalDateTime.now());
        finalizeIfBothSigned(wc);
        return repo.save(wc);
    }

    /** BP측 사인. */
    @Transactional
    public WorkConfirmation signBp(Long id, byte[] png, AuthenticatedUser actor) {
        WorkConfirmation wc = get(id, actor);
        ensureBpActor(wc, actor);
        if (wc.getStatus() == WorkConfirmationStatus.CANCELLED) {
            throw ApiException.badRequest("CANCELLED", "취소된 작업확인서");
        }
        wc.setBpSignerUserId(actor.id());
        wc.setBpSignerName(actor.name());
        wc.setBpSignaturePng(png);
        wc.setBpSignedAt(LocalDateTime.now());
        finalizeIfBothSigned(wc);
        return repo.save(wc);
    }

    @Transactional
    public WorkConfirmation cancel(Long id, AuthenticatedUser actor) {
        WorkConfirmation wc = get(id, actor);
        // 발급 공급사 본인 또는 ADMIN 만 취소 가능
        if (actor.role() != Role.ADMIN
                && (actor.companyId() == null
                    || !actor.companyId().equals(wc.getIssuingSupplierCompanyId()))) {
            throw ApiException.forbidden("CANCEL_DENIED", "발급 공급사 또는 ADMIN 만 취소 가능합니다");
        }
        wc.setStatus(WorkConfirmationStatus.CANCELLED);
        return repo.save(wc);
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private WorkPlan workPlanOrThrow(Long workPlanId) {
        return workPlanRepo.findById(workPlanId)
                .orElseThrow(() -> ApiException.notFound("WORK_PLAN_NOT_FOUND", "작업계획서를 찾을 수 없습니다"));
    }

    private void ensureCanView(AuthenticatedUser actor, WorkPlan wp, Long supplierCompanyIdFilter) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            if (actor.companyId() != null && actor.companyId().equals(wp.getBpCompanyId())) return;
        }
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
            }
            // 단건 조회는 supplier 일치 강제 / 목록은 list 안에서 가시 필터링 (현재는 통과 — 컨트롤러가 워크플랜 자기 자원 검증 수행)
            if (supplierCompanyIdFilter != null && !actor.companyId().equals(supplierCompanyIdFilter)) {
                throw ApiException.forbidden("VIEW_DENIED", "다른 공급사 작업확인서는 조회할 수 없습니다");
            }
            return;
        }
        throw ApiException.forbidden("VIEW_DENIED", "조회 권한이 없습니다");
    }

    private void ensureSupplierActor(WorkConfirmation wc, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.companyId() == null || !actor.companyId().equals(wc.getIssuingSupplierCompanyId())) {
            throw ApiException.forbidden("SUPPLIER_SIGN_DENIED",
                    "발급 공급사 본인 또는 ADMIN 만 공급사측 사인이 가능합니다");
        }
    }

    private void ensureBpActor(WorkConfirmation wc, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() != Role.BP || actor.companyId() == null
                || !actor.companyId().equals(wc.getBpCompanyId())) {
            throw ApiException.forbidden("BP_SIGN_DENIED",
                    "발주 BP 본인 또는 ADMIN 만 BP측 사인이 가능합니다");
        }
    }

    private static void validateHourSlot(BigDecimal h, String label) {
        if (h == null) return;
        if (h.compareTo(BigDecimal.ZERO) < 0 || h.compareTo(new BigDecimal("24")) > 0) {
            throw ApiException.badRequest("HOUR_OUT_OF_RANGE", label + " 시간은 0~24 사이여야 합니다");
        }
    }

    private void recalcTotal(WorkConfirmation wc) {
        BigDecimal total = BigDecimal.ZERO;
        if (wc.getMorningHours() != null) total = total.add(wc.getMorningHours());
        if (wc.getAfternoonHours() != null) total = total.add(wc.getAfternoonHours());
        if (wc.getOvertimeHours() != null) total = total.add(wc.getOvertimeHours());
        if (wc.getNightHours() != null) total = total.add(wc.getNightHours());
        wc.setTotalHours(total);
    }

    private void finalizeIfBothSigned(WorkConfirmation wc) {
        if (wc.getSupplierSignedAt() != null && wc.getBpSignedAt() != null) {
            wc.setStatus(WorkConfirmationStatus.COMPLETED);
        } else if (wc.getStatus() == WorkConfirmationStatus.INVALIDATED) {
            wc.setStatus(WorkConfirmationStatus.PENDING);
        }
    }

    public static class UpdateRequest {
        public String workContent;
        public String remarks;
        public String morningTime;
        public BigDecimal morningHours;
        public String afternoonTime;
        public BigDecimal afternoonHours;
        public String overtimeTime;
        public BigDecimal overtimeHours;
        public String nightTime;
        public BigDecimal nightHours;
        public Long attendancePhotoDocId;
    }
}
