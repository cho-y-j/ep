package com.skep.quotation.dispatch.quote;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationLabels;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.dispatch.DispatchedEquipment;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 견적서 .xlsx 생성기. 양식: 하이드로_거미_한성크린텍 템플릿 구조.
 * - per-supplier: 공급사 1곳의 DispatchedEquipment 라인을 카테고리별로 묶어 출력
 * - comparison: 한 견적요청에 여러 공급사가 보낸 라인을 좌측 (카테고리, 규격) 행 × 우측 공급사 열로 비교
 */
@Service
@RequiredArgsConstructor
public class QuotationExcelService {

    private final QuotationRequestRepository requests;
    private final DispatchedEquipmentRepository dispatched;
    private final EquipmentRepository equipments;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final UserRepository users;

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /* ===================== Per-supplier 견적서 ===================== */

    @Transactional(readOnly = true)
    public byte[] buildSupplierQuote(Long requestId, Long supplierCompanyId) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        Company supplier = companies.findById(supplierCompanyId)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "공급사 없음"));
        Long bpId = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
        Company bp = bpId != null ? companies.findById(bpId).orElse(null) : null;
        Site site = qr.getSiteId() != null ? sites.findById(qr.getSiteId()).orElse(null) : null;

        List<DispatchedEquipment> lines = dispatched.findByQuotationRequestIdAndSupplierCompanyId(requestId, supplierCompanyId);
        if (lines.isEmpty()) {
            throw ApiException.badRequest("NO_LINES", "발송된 견적 라인이 없습니다");
        }
        Map<Long, Equipment> eqMap = equipments.findAllById(
                lines.stream().map(DispatchedEquipment::getEquipmentId).filter(java.util.Objects::nonNull).toList()
        ).stream().collect(Collectors.toMap(Equipment::getId, e -> e));

        List<User> contacts = users.findByCompanyIdOrderByIdAsc(supplierCompanyId).stream()
                .filter(User::isShowInQuote)
                .sorted(Comparator.comparing(u -> u.getQuoteDisplayOrder() == null ? Integer.MAX_VALUE : u.getQuoteDisplayOrder()))
                .limit(4)
                .toList();

        String fallbackCategory = qr.getEquipmentCategory() != null
                ? NotificationLabels.equipmentCategory(qr.getEquipmentCategory()) : null;
        String fallbackSpec = qr.getSpecText();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet(safeSheetName(supplier.getName()));
            Styles s = new Styles(wb);
            renderHeader(sh, s, qr, bp, site, supplier, contacts, requestId);
            int firstBodyRow = 13;
            int lastBodyRow = renderBody(sh, s, lines, eqMap, firstBodyRow, fallbackCategory, fallbackSpec);
            renderTotal(sh, s, lines, firstBodyRow - 3, lastBodyRow);
            renderFooter(sh, s, lastBodyRow + 1);
            setColumnWidths(sh);
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("xlsx 생성 실패", e);
        }
    }

    /* ===================== Comparison ===================== */

    @Transactional(readOnly = true)
    public byte[] buildComparison(Long requestId) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        List<DispatchedEquipment> all = dispatched.findByQuotationRequestId(requestId);
        if (all.isEmpty()) {
            throw ApiException.badRequest("NO_LINES", "발송된 견적이 없습니다");
        }
        Map<Long, Equipment> eqMap = equipments.findAllById(
                all.stream().map(DispatchedEquipment::getEquipmentId).distinct().toList()
        ).stream().collect(Collectors.toMap(Equipment::getId, e -> e));

        // 공급사 목록 (열)
        List<Long> supplierIds = all.stream().map(DispatchedEquipment::getSupplierCompanyId).distinct().sorted().toList();
        Map<Long, Company> supplierMap = companies.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c));

        // 카테고리 + 규격 별 행 키
        // 키: 카테고리Korean + "/" + model(spec)
        record RowKey(String category, String spec) {}
        Map<RowKey, Map<Long, DispatchedEquipment>> grid = new LinkedHashMap<>();
        for (DispatchedEquipment d : all) {
            Equipment e = eqMap.get(d.getEquipmentId());
            if (e == null) continue;
            RowKey k = new RowKey(categoryLabel(e), specOf(e));
            grid.computeIfAbsent(k, x -> new HashMap<>())
                    .put(d.getSupplierCompanyId(), d);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("견적비교");
            Styles s = new Styles(wb);

            // Title
            Row t = sh.createRow(0);
            Cell tc = t.createCell(0);
            tc.setCellValue("견 적 비 교 표 — 견적요청 #" + requestId);
            tc.setCellStyle(s.title);
            int totalCols = 2 + supplierIds.size() * 4; // 품명/규격 + supplier × 4
            sh.addMergedRegion(new CellRangeAddress(0, 0, 0, totalCols - 1));

            // Header row 1 — supplier name spanning 4 cols
            Row h1 = sh.createRow(2);
            Cell hc0 = h1.createCell(0); hc0.setCellValue("품명"); hc0.setCellStyle(s.headerCenter);
            Cell hc1 = h1.createCell(1); hc1.setCellValue("규격"); hc1.setCellStyle(s.headerCenter);
            int col = 2;
            for (Long supId : supplierIds) {
                Cell c = h1.createCell(col);
                c.setCellValue(supplierMap.get(supId) != null ? supplierMap.get(supId).getName() : "공급사#" + supId);
                c.setCellStyle(s.headerCenter);
                sh.addMergedRegion(new CellRangeAddress(2, 2, col, col + 3));
                for (int k = 1; k < 4; k++) h1.createCell(col + k).setCellStyle(s.headerCenter);
                col += 4;
            }
            // merge 품명/규격 vertically
            sh.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));
            sh.addMergedRegion(new CellRangeAddress(2, 3, 1, 1));

            // Header row 2 — sub headers
            Row h2 = sh.createRow(3);
            col = 2;
            for (int i = 0; i < supplierIds.size(); i++) {
                String[] subs = {"일대", "OT일대", "월대", "OT월대"};
                for (int k = 0; k < 4; k++) {
                    Cell c = h2.createCell(col + k);
                    c.setCellValue(subs[k]);
                    c.setCellStyle(s.headerCenter);
                }
                col += 4;
            }

            // Body
            int r = 4;
            for (var entry : grid.entrySet()) {
                Row row = sh.createRow(r);
                Cell nameCell = row.createCell(0);
                nameCell.setCellValue(entry.getKey().category());
                nameCell.setCellStyle(s.bodyCenter);
                Cell specCell = row.createCell(1);
                specCell.setCellValue(entry.getKey().spec());
                specCell.setCellStyle(s.bodyCenter);
                col = 2;
                for (Long supId : supplierIds) {
                    DispatchedEquipment d = entry.getValue().get(supId);
                    fillPrice(row, col, d == null ? null : d.getDailyPrice(), s);
                    fillPrice(row, col + 1, d == null ? null : d.getOtDailyPrice(), s);
                    fillPrice(row, col + 2, d == null ? null : d.getMonthlyPrice(), s);
                    fillPrice(row, col + 3, d == null ? null : d.getOtMonthlyPrice(), s);
                    col += 4;
                }
                r++;
            }

            // Column widths
            sh.setColumnWidth(0, 16 * 256);
            sh.setColumnWidth(1, 12 * 256);
            for (int i = 0; i < supplierIds.size() * 4; i++) {
                sh.setColumnWidth(2 + i, 12 * 256);
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("xlsx 생성 실패", e);
        }
    }

    /* ===================== 미리보기 (DB write 없이 즉시) ===================== */

    public record PreviewRates(Long dailyPrice, Long otDailyPrice, Long monthlyPrice, Long otMonthlyPrice, String notes,
                                String dailyNote, String otDailyNote, String monthlyNote, String otMonthlyNote) {}

    @Transactional(readOnly = true)
    public byte[] buildPreviewXlsx(Long requestId, Long supplierCompanyId, PreviewRates rates) {
        DispatchedEquipment phantom = previewRow(requestId, supplierCompanyId, rates);
        return buildSupplierQuoteFromLines(requestId, supplierCompanyId, List.of(phantom));
    }

    @Transactional(readOnly = true)
    public byte[] buildPreviewPdf(Long requestId, Long supplierCompanyId, PreviewRates rates) {
        DispatchedEquipment phantom = previewRow(requestId, supplierCompanyId, rates);
        return buildSupplierQuotePdfFromLines(requestId, supplierCompanyId, List.of(phantom));
    }

    private DispatchedEquipment previewRow(Long requestId, Long supplierCompanyId, PreviewRates rates) {
        return DispatchedEquipment.builder()
                .quotationRequestId(requestId)
                .supplierCompanyId(supplierCompanyId)
                .equipmentId(null)
                .dailyPrice(rates.dailyPrice())
                .otDailyPrice(rates.otDailyPrice())
                .monthlyPrice(rates.monthlyPrice())
                .otMonthlyPrice(rates.otMonthlyPrice())
                .notes(rates.notes())
                .dailyNote(rates.dailyNote())
                .otDailyNote(rates.otDailyNote())
                .monthlyNote(rates.monthlyNote())
                .otMonthlyNote(rates.otMonthlyNote())
                .build();
    }

    /** DB lines 대신 외부에서 주입한 lines 로 양식 생성. buildSupplierQuote 의 본문 재사용. */
    private byte[] buildSupplierQuoteFromLines(Long requestId, Long supplierCompanyId, List<DispatchedEquipment> lines) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        Company supplier = companies.findById(supplierCompanyId)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "공급사 없음"));
        Long bpId = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
        Company bp = bpId != null ? companies.findById(bpId).orElse(null) : null;
        Site site = qr.getSiteId() != null ? sites.findById(qr.getSiteId()).orElse(null) : null;
        Map<Long, Equipment> eqMap = equipments.findAllById(
                lines.stream().map(DispatchedEquipment::getEquipmentId).filter(java.util.Objects::nonNull).toList()
        ).stream().collect(Collectors.toMap(Equipment::getId, e -> e));
        List<User> contacts = users.findByCompanyIdOrderByIdAsc(supplierCompanyId).stream()
                .filter(User::isShowInQuote)
                .sorted(Comparator.comparing(u -> u.getQuoteDisplayOrder() == null ? Integer.MAX_VALUE : u.getQuoteDisplayOrder()))
                .limit(4).toList();
        String fallbackCategory = qr.getEquipmentCategory() != null
                ? NotificationLabels.equipmentCategory(qr.getEquipmentCategory()) : null;
        String fallbackSpec = qr.getSpecText();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet(safeSheetName(supplier.getName()));
            Styles s = new Styles(wb);
            renderHeader(sh, s, qr, bp, site, supplier, contacts, requestId);
            int firstBodyRow = 13;
            int lastBodyRow = renderBody(sh, s, lines, eqMap, firstBodyRow, fallbackCategory, fallbackSpec);
            renderTotal(sh, s, lines, firstBodyRow - 3, lastBodyRow);
            renderFooter(sh, s, lastBodyRow + 1);
            setColumnWidths(sh);
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("xlsx 생성 실패", e);
        }
    }

    private byte[] buildSupplierQuotePdfFromLines(Long requestId, Long supplierCompanyId, List<DispatchedEquipment> lines) {
        return buildSupplierQuotePdfInternal(requestId, supplierCompanyId, lines);
    }

    /* ===================== Proposal(응찰) 견적서 — BP 측 미리보기 ===================== */

    /** QuotationProposal 1건을 견적서 양식(.xlsx)으로 렌더. */
    @Transactional(readOnly = true)
    public byte[] buildProposalQuoteXlsx(com.skep.quotation.proposal.QuotationProposal p) {
        DispatchedEquipment fake = proposalToFakeDispatched(p);
        return buildSupplierQuoteFromLines(p.getRequestId(), p.getSupplierCompanyId(), List.of(fake));
    }

    /** QuotationProposal 1건을 견적서 양식(.pdf)으로 렌더. */
    @Transactional(readOnly = true)
    public byte[] buildProposalQuotePdf(com.skep.quotation.proposal.QuotationProposal p) {
        DispatchedEquipment fake = proposalToFakeDispatched(p);
        return buildSupplierQuotePdfInternal(p.getRequestId(), p.getSupplierCompanyId(), List.of(fake));
    }

    /** Proposal → DispatchedEquipment 빌더 매핑 (DB 저장 X, 렌더링용 ephemeral). */
    private DispatchedEquipment proposalToFakeDispatched(com.skep.quotation.proposal.QuotationProposal p) {
        return DispatchedEquipment.builder()
                .quotationRequestId(p.getRequestId())
                .supplierCompanyId(p.getSupplierCompanyId())
                .equipmentId(p.getEquipmentId())
                .dailyPrice(p.getDailyRate() != null ? p.getDailyRate().longValue() : null)
                .otDailyPrice(p.getOtDailyRate() != null ? p.getOtDailyRate().longValue() : null)
                .monthlyPrice(p.getMonthlyRate() != null ? p.getMonthlyRate().longValue() : null)
                .otMonthlyPrice(p.getOtMonthlyRate() != null ? p.getOtMonthlyRate().longValue() : null)
                .notes(p.getNote())
                .dailyNote(p.getDailyNote())
                .otDailyNote(p.getOtDailyNote())
                .monthlyNote(p.getMonthlyNote())
                .otMonthlyNote(p.getOtMonthlyNote())
                .build();
    }

    /* ===================== Per-supplier PDF ===================== */

    @Transactional(readOnly = true)
    public byte[] buildSupplierQuotePdf(Long requestId, Long supplierCompanyId) {
        List<DispatchedEquipment> lines = dispatched.findByQuotationRequestIdAndSupplierCompanyId(requestId, supplierCompanyId);
        if (lines.isEmpty()) throw ApiException.badRequest("NO_LINES", "발송된 견적 라인이 없습니다");
        return buildSupplierQuotePdfInternal(requestId, supplierCompanyId, lines);
    }

    private byte[] buildSupplierQuotePdfInternal(Long requestId, Long supplierCompanyId, List<DispatchedEquipment> lines) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        Company supplier = companies.findById(supplierCompanyId)
                .orElseThrow(() -> ApiException.notFound("SUPPLIER_NOT_FOUND", "공급사 없음"));
        Long bpId = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
        Company bp = bpId != null ? companies.findById(bpId).orElse(null) : null;
        Site site = qr.getSiteId() != null ? sites.findById(qr.getSiteId()).orElse(null) : null;
        Map<Long, Equipment> eqMap = equipments.findAllById(
                lines.stream().map(DispatchedEquipment::getEquipmentId).filter(java.util.Objects::nonNull).toList()
        ).stream().collect(Collectors.toMap(Equipment::getId, e -> e));
        List<User> contacts = users.findByCompanyIdOrderByIdAsc(supplierCompanyId).stream()
                .filter(User::isShowInQuote)
                .sorted(Comparator.comparing(u -> u.getQuoteDisplayOrder() == null ? Integer.MAX_VALUE : u.getQuoteDisplayOrder()))
                .limit(4).toList();
        String fallbackCategory = qr.getEquipmentCategory() != null
                ? NotificationLabels.equipmentCategory(qr.getEquipmentCategory()) : null;
        String fallbackSpec = qr.getSpecText();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {
            PdfFont font = loadKoreanFont();
            doc.setFont(font);

            doc.add(new Paragraph("견   적   서").setTextAlignment(TextAlignment.CENTER).setFontSize(20).setBold().setMarginBottom(8));

            // 헤더 박스 (2열): 좌측 발주처/현장, 우측 공급자 박스
            Table head = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            head.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(LocalDate.now().format(DATE)).setFontSize(9))
                    .add(new Paragraph((bp != null ? bp.getName() : "") + "  귀하").setBold())
                    .add(new Paragraph(site != null ? site.getName() : nz(qr.getWorkLocationText())).setFontSize(10))
                    .add(new Paragraph("귀사의 일익번창을 기원합니다.").setFontSize(9))
                    .add(new Paragraph("아래와 같이 견적서를 제출합니다.").setFontSize(9)));
            // 공급자 박스
            Table sup = new Table(UnitValue.createPercentArray(new float[]{1, 2})).useAllAvailableWidth();
            sup.setFontSize(9);
            sup.addCell(boxCell("등록번호", true)); sup.addCell(boxCell(nz(supplier.getBusinessNumber()), false));
            sup.addCell(boxCell("상호(법인명)", true)); sup.addCell(boxCell(supplier.getName(), false));
            sup.addCell(boxCell("성명", true)); sup.addCell(boxCell(nz(supplier.getCeoName()), false));
            sup.addCell(boxCell("사업장주소", true)); sup.addCell(boxCell(nz(supplier.getBusinessAddress()), false));
            sup.addCell(boxCell("업태", true)); sup.addCell(boxCell(nz(supplier.getBusinessCategory()), false));
            sup.addCell(boxCell("종목", true)); sup.addCell(boxCell(nz(supplier.getBusinessSubcategory()), false));
            sup.addCell(boxCell("전화번호", true)); sup.addCell(boxCell(nz(supplier.getPhone()), false));
            sup.addCell(boxCell("팩스", true)); sup.addCell(boxCell(nz(supplier.getFax()), false));
            head.addCell(new com.itextpdf.layout.element.Cell().add(sup));
            doc.add(head);

            // 합계
            long sum = lines.stream().mapToLong(d -> nzLong(d.getDailyPrice()) + nzLong(d.getOtDailyPrice())
                    + nzLong(d.getMonthlyPrice()) + nzLong(d.getOtMonthlyPrice())).sum();
            doc.add(new Paragraph("합계금액: " + MONEY.format(sum) + " 원 (공급가액+세액)")
                    .setBold().setTextAlignment(TextAlignment.CENTER).setMarginTop(8).setMarginBottom(4));

            // 본문 테이블
            Table body = new Table(UnitValue.createPercentArray(new float[]{16, 12, 7, 14, 14, 7, 30}))
                    .useAllAvailableWidth();
            body.setFontSize(9);
            String[] hdrs = {"품명", "규격", "수량", "단가", "공급가액", "세액", "비고"};
            for (String h : hdrs) body.addHeaderCell(headerCell(h));

            Map<String, List<DispatchedEquipment>> grouped = new LinkedHashMap<>();
            for (DispatchedEquipment d : lines) {
                Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
                String cat = e != null ? categoryLabel(e) : (fallbackCategory != null ? fallbackCategory : "장비");
                grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(d);
            }
            for (var entry : grouped.entrySet()) {
                int catRows = entry.getValue().stream().mapToInt(d -> 4 + (d.getNotes() != null && !d.getNotes().isBlank() ? 1 : 0)).sum();
                body.addCell(new com.itextpdf.layout.element.Cell(catRows, 1).add(new Paragraph(entry.getKey())).setTextAlignment(TextAlignment.CENTER));
                for (DispatchedEquipment d : entry.getValue()) {
                    Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
                    String spec = e != null ? specOf(e) : (fallbackSpec != null ? fallbackSpec : "");
                    addQuoteRow(body, spec, "1", "일대", money(d.getDailyPrice()), "별도", nz(d.getDailyNote()));
                    addQuoteRow(body, "", "", "OT/h (일대)", money(d.getOtDailyPrice()), "\"", nz(d.getOtDailyNote()));
                    addQuoteRow(body, "", "", "월임대", money(d.getMonthlyPrice()), "\"", nz(d.getMonthlyNote()));
                    addQuoteRow(body, "", "", "OT/h (월대)", money(d.getOtMonthlyPrice()), "\"", nz(d.getOtMonthlyNote()));
                    if (d.getNotes() != null && !d.getNotes().isBlank()) {
                        body.addCell(new com.itextpdf.layout.element.Cell(1, 5).add(new Paragraph("비고: " + d.getNotes())));
                        body.addCell(""); body.addCell("");
                    }
                }
            }
            doc.add(body);

            // 담당자
            if (!contacts.isEmpty()) {
                Paragraph ct = new Paragraph().setFontSize(9).setMarginTop(6);
                for (User u : contacts) {
                    ct.add("담당자: " + nz(u.getName()) + (u.getPhone() != null ? " (" + u.getPhone() + ")" : "") + "    ");
                }
                doc.add(ct);
            }
            doc.add(new Paragraph("우리는 귀사의 미래와 함께하길 원합니다.")
                    .setTextAlignment(TextAlignment.CENTER).setFontSize(9).setMarginTop(8));
            doc.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("pdf 생성 실패", e);
        }
    }

    /* ===================== Comparison PDF ===================== */

    @Transactional(readOnly = true)
    public byte[] buildComparisonPdf(Long requestId) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        List<DispatchedEquipment> all = dispatched.findByQuotationRequestId(requestId);
        if (all.isEmpty()) throw ApiException.badRequest("NO_LINES", "발송된 견적이 없습니다");
        Map<Long, Equipment> eqMap = equipments.findAllById(
                all.stream().map(DispatchedEquipment::getEquipmentId).distinct().toList()
        ).stream().collect(Collectors.toMap(Equipment::getId, e -> e));
        List<Long> supplierIds = all.stream().map(DispatchedEquipment::getSupplierCompanyId).distinct().sorted().toList();
        Map<Long, Company> supplierMap = companies.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c));

        record RowKey(String category, String spec) {}
        Map<RowKey, Map<Long, DispatchedEquipment>> grid = new LinkedHashMap<>();
        for (DispatchedEquipment d : all) {
            Equipment e = eqMap.get(d.getEquipmentId());
            if (e == null) continue;
            grid.computeIfAbsent(new RowKey(categoryLabel(e), specOf(e)), x -> new HashMap<>())
                    .put(d.getSupplierCompanyId(), d);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4.rotate())) {
            PdfFont font = loadKoreanFont();
            doc.setFont(font);
            doc.add(new Paragraph("견  적  비  교  표 — 견적요청 #" + requestId)
                    .setTextAlignment(TextAlignment.CENTER).setFontSize(16).setBold().setMarginBottom(6));

            float[] widths = new float[2 + supplierIds.size() * 4];
            widths[0] = 14; widths[1] = 10;
            for (int i = 2; i < widths.length; i++) widths[i] = 8;
            Table t = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
            t.setFontSize(8);

            // header row 1: 품명/규격 + supplier name (span 4)
            t.addHeaderCell(new com.itextpdf.layout.element.Cell(2, 1).add(new Paragraph("품명")).setTextAlignment(TextAlignment.CENTER));
            t.addHeaderCell(new com.itextpdf.layout.element.Cell(2, 1).add(new Paragraph("규격")).setTextAlignment(TextAlignment.CENTER));
            for (Long supId : supplierIds) {
                String name = supplierMap.get(supId) != null ? supplierMap.get(supId).getName() : "공급사#" + supId;
                t.addHeaderCell(new com.itextpdf.layout.element.Cell(1, 4).add(new Paragraph(name)).setTextAlignment(TextAlignment.CENTER));
            }
            // header row 2: 4 sub headers per supplier
            for (int i = 0; i < supplierIds.size(); i++) {
                for (String sub : new String[]{"일대", "OT일대", "월대", "OT월대"}) {
                    t.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(sub)).setTextAlignment(TextAlignment.CENTER));
                }
            }
            // body
            for (var entry : grid.entrySet()) {
                t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(entry.getKey().category())).setTextAlignment(TextAlignment.CENTER));
                t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(entry.getKey().spec())).setTextAlignment(TextAlignment.CENTER));
                for (Long supId : supplierIds) {
                    DispatchedEquipment d = entry.getValue().get(supId);
                    t.addCell(cellMoney(d == null ? null : d.getDailyPrice()));
                    t.addCell(cellMoney(d == null ? null : d.getOtDailyPrice()));
                    t.addCell(cellMoney(d == null ? null : d.getMonthlyPrice()));
                    t.addCell(cellMoney(d == null ? null : d.getOtMonthlyPrice()));
                }
            }
            doc.add(t);
            doc.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("pdf 생성 실패", e);
        }
    }

    private com.itextpdf.layout.element.Cell headerCell(String text) {
        return new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text).setBold())
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(new DeviceRgb(230, 230, 230));
    }

    private com.itextpdf.layout.element.Cell boxCell(String text, boolean header) {
        com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text));
        if (header) {
            c.setBold().setBackgroundColor(new DeviceRgb(230, 230, 230));
        }
        return c.setTextAlignment(TextAlignment.CENTER);
    }

    private com.itextpdf.layout.element.Cell cellMoney(Long v) {
        return new com.itextpdf.layout.element.Cell().add(new Paragraph(v == null ? "-" : MONEY.format(v)))
                .setTextAlignment(TextAlignment.RIGHT);
    }

    private void addQuoteRow(Table t, String name, String qty, String label, String price, String tax, String remark) {
        if (!name.isEmpty()) t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(name)).setTextAlignment(TextAlignment.CENTER));
        else t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("")));
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(qty)).setTextAlignment(TextAlignment.CENTER));
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(label)).setTextAlignment(TextAlignment.CENTER));
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(price)).setTextAlignment(TextAlignment.RIGHT));
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(tax)).setTextAlignment(TextAlignment.CENTER));
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(remark)));
    }

    private PdfFont loadKoreanFont() throws IOException {
        try {
            return PdfFontFactory.createFont(new ClassPathResource("fonts/NanumGothic.ttf").getURL().toString(),
                    PdfEncodings.IDENTITY_H);
        } catch (Exception ex) {
            return PdfFontFactory.createFont("KSCpc-EUC-H", PdfEncodings.IDENTITY_H);
        }
    }

    /* ===================== Rendering helpers — 원본 양식 16열(A~P) 기준 ===================== */

    /** 원본: A2:H2 견적서, B1:H1 NO., B4:C4 날짜, B5:C6 발주처, D5:D6 귀하,
     *  K5:K9 "공급자" 세로 라벨, L/M/N/O/P 공급자 정보, B7:C7 현장, B9:D9 인사말,
     *  B11~B12 합계금액, L12+ 담당자, 13행 본문 컬럼 헤더. */
    private void renderHeader(Sheet sh, Styles s, QuotationRequest qr, Company bp, Site site,
                              Company supplier, List<User> contacts, Long requestId) {
        // row 1 (0-idx 0): NO.
        Row r1 = sh.createRow(0);
        Cell a1 = r1.createCell(0); a1.setCellValue("ㄱ"); a1.setCellStyle(s.bodyLeft);
        Cell no = r1.createCell(1);
        no.setCellValue("NO." + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + requestId);
        no.setCellStyle(s.right);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 1, 7));

        // row 2: 견적서 큰 타이틀 A2:H2
        Row r2 = sh.createRow(1);
        Cell title = r2.createCell(0); title.setCellValue("견     적     서"); title.setCellStyle(s.title);
        sh.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));
        for (int c = 1; c <= 7; c++) r2.createCell(c).setCellStyle(s.title);

        // row 3: 공백 (병합만)
        sh.createRow(2);

        // row 4: 날짜 B4:C4
        Row r4 = sh.createRow(3);
        Cell d = r4.createCell(1); d.setCellValue(LocalDate.now().format(DATE));
        d.setCellStyle(s.dateCenter);
        sh.addMergedRegion(new CellRangeAddress(3, 3, 1, 2));

        // row 5-6: 발주처(B5:C6) / 귀하(D5:D6) / 공급자 박스 첫 행
        Row r5 = sh.createRow(4);
        Row r6 = sh.createRow(5);
        Cell bpCell = r5.createCell(1); bpCell.setCellValue(bp != null ? bp.getName() : "");
        bpCell.setCellStyle(s.bpCompanyBig);
        sh.addMergedRegion(new CellRangeAddress(4, 5, 1, 2));
        Cell gui = r5.createCell(3); gui.setCellValue("귀하"); gui.setCellStyle(s.guiha);
        sh.addMergedRegion(new CellRangeAddress(4, 5, 3, 3));

        // K5:K9 = "공 급 자" 세로 5행
        Cell gongHead = r5.createCell(10); gongHead.setCellValue("공  급  자"); gongHead.setCellStyle(s.vertLabel);
        sh.addMergedRegion(new CellRangeAddress(4, 8, 10, 10));
        // L5=등록번호 라벨, M5:P5=값
        Cell regLabel = r5.createCell(11); regLabel.setCellValue("등록번호"); regLabel.setCellStyle(s.box);
        Cell regVal = r5.createCell(12); regVal.setCellValue(nz(supplier.getBusinessNumber())); regVal.setCellStyle(s.boxValue);
        sh.addMergedRegion(new CellRangeAddress(4, 4, 12, 15));

        // row 6: 상호(법인명) + 성명
        Cell sangLabel = r6.createCell(11); sangLabel.setCellValue("상호(법인명)"); sangLabel.setCellStyle(s.box);
        Cell sangVal = r6.createCell(12); sangVal.setCellValue(supplier.getName()); sangVal.setCellStyle(s.boxValue);
        Cell ceoLabel = r6.createCell(13); ceoLabel.setCellValue("성명"); ceoLabel.setCellStyle(s.box);
        Cell ceoVal = r6.createCell(14); ceoVal.setCellValue(nz(supplier.getCeoName())); ceoVal.setCellStyle(s.boxValue);
        sh.addMergedRegion(new CellRangeAddress(5, 5, 14, 15));

        // row 7: 현장 B7:C7 + 사업장주소 M7:P7
        Row r7 = sh.createRow(6);
        Cell siteCell = r7.createCell(1); siteCell.setCellValue(site != null ? site.getName() : nz(qr.getWorkLocationText()));
        siteCell.setCellStyle(s.siteBig);
        sh.addMergedRegion(new CellRangeAddress(6, 6, 1, 2));
        Cell addrLabel = r7.createCell(11); addrLabel.setCellValue("사업장주소"); addrLabel.setCellStyle(s.box);
        Cell addrVal = r7.createCell(12); addrVal.setCellValue(nz(supplier.getBusinessAddress())); addrVal.setCellStyle(s.boxValueLeft);
        sh.addMergedRegion(new CellRangeAddress(6, 6, 12, 15));

        // row 8: 업태/종목
        Row r8 = sh.createRow(7);
        Cell upLabel = r8.createCell(11); upLabel.setCellValue("업태"); upLabel.setCellStyle(s.box);
        Cell upVal = r8.createCell(12); upVal.setCellValue(nz(supplier.getBusinessCategory())); upVal.setCellStyle(s.boxValue);
        Cell jongLabel = r8.createCell(13); jongLabel.setCellValue("종목"); jongLabel.setCellStyle(s.box);
        Cell jongVal = r8.createCell(14); jongVal.setCellValue(nz(supplier.getBusinessSubcategory())); jongVal.setCellStyle(s.boxValue);
        sh.addMergedRegion(new CellRangeAddress(7, 7, 14, 15));
        // B8:D8 빈
        sh.addMergedRegion(new CellRangeAddress(7, 7, 1, 3));

        // row 9: 인사말 B9:D9 + 전화/팩스
        Row r9 = sh.createRow(8);
        Cell ins = r9.createCell(1); ins.setCellValue("귀사의 일익번창을 기원합니다.");
        ins.setCellStyle(s.bodyLeft12);
        sh.addMergedRegion(new CellRangeAddress(8, 8, 1, 3));
        Cell telLabel = r9.createCell(11); telLabel.setCellValue("전화번호"); telLabel.setCellStyle(s.box);
        Cell telVal = r9.createCell(12); telVal.setCellValue(nz(supplier.getPhone())); telVal.setCellStyle(s.boxValue);
        Cell faxLabel = r9.createCell(13); faxLabel.setCellValue("팩스"); faxLabel.setCellStyle(s.box);
        Cell faxVal = r9.createCell(14); faxVal.setCellValue(nz(supplier.getFax())); faxVal.setCellStyle(s.boxValue);
        sh.addMergedRegion(new CellRangeAddress(8, 8, 14, 15));

        // row 10: 인사말 2
        Row r10 = sh.createRow(9);
        Cell sub = r10.createCell(1); sub.setCellValue("아래와 같이 견적서를 제출합니다."); sub.setCellStyle(s.bodyLeft12);

        // row 11~12: 합계금액 영역. B11 라벨, C11:D11 값표시 빈 자리, E11 "원整", F11:G11 빈, B12 부제, C12:H12 합계금액 자리.
        Row r11 = sh.createRow(10);
        Cell sumLabel = r11.createCell(1); sumLabel.setCellValue("합  계  금  액"); sumLabel.setCellStyle(s.sumLabel);
        // C11:D11 (빈, 값 placeholder)
        Cell sumPad = r11.createCell(2); sumPad.setCellStyle(s.sumValue);
        r11.createCell(3).setCellStyle(s.sumValue);
        sh.addMergedRegion(new CellRangeAddress(10, 10, 2, 3));
        Cell wonLabel = r11.createCell(4); wonLabel.setCellValue("원"); wonLabel.setCellStyle(s.sumWon);
        r11.createCell(5).setCellStyle(s.sumValue);
        r11.createCell(6).setCellStyle(s.sumValue);
        sh.addMergedRegion(new CellRangeAddress(10, 10, 5, 6));
        Row r12 = sh.createRow(11);
        Cell sumSub = r12.createCell(1); sumSub.setCellValue("(공급가액+세액)"); sumSub.setCellStyle(s.sumSub);
        Cell sumValRow = r12.createCell(2); sumValRow.setCellStyle(s.sumValue);
        for (int c = 3; c <= 7; c++) r12.createCell(c).setCellStyle(s.sumValue);
        sh.addMergedRegion(new CellRangeAddress(11, 11, 2, 7));

        // 담당자 4명 (L12~L15) — 노출 가능한 수만큼
        for (int i = 0; i < contacts.size() && i < 4; i++) {
            int rowIdx = 11 + i;
            Row cr = sh.getRow(rowIdx) != null ? sh.getRow(rowIdx) : sh.createRow(rowIdx);
            Cell cc = cr.createCell(11);
            User u = contacts.get(i);
            cc.setCellValue("담당자 : " + nz(u.getName()) + (u.getPhone() != null ? "(" + u.getPhone() + ")" : ""));
            cc.setCellStyle(s.contact);
            sh.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 11, 15));
        }

        // row 13: 본문 컬럼 헤더 A:B(품명) C(규격) D(수량) E(단가) F(공급가액) G(세액) H(비고)
        Row hdr = sh.createRow(12);
        Cell c0 = hdr.createCell(0); c0.setCellValue("품           명"); c0.setCellStyle(s.colHeader);
        Cell c1 = hdr.createCell(1); c1.setCellStyle(s.colHeader);
        sh.addMergedRegion(new CellRangeAddress(12, 12, 0, 1));
        Cell c2 = hdr.createCell(2); c2.setCellValue("규  격"); c2.setCellStyle(s.colHeader);
        Cell c3 = hdr.createCell(3); c3.setCellValue("수  량"); c3.setCellStyle(s.colHeader);
        Cell c4 = hdr.createCell(4); c4.setCellValue("단  가"); c4.setCellStyle(s.colHeader);
        Cell c5 = hdr.createCell(5); c5.setCellValue("공급가액"); c5.setCellStyle(s.colHeader);
        Cell c6 = hdr.createCell(6); c6.setCellValue("세  액"); c6.setCellStyle(s.colHeader);
        Cell c7 = hdr.createCell(7); c7.setCellValue("비고"); c7.setCellStyle(s.colHeader);
    }

    /** 원본 양식 본문:
     *  - A:B 머지(품명, 그룹 전체)
     *  - C 머지(규격, 4행) — 각 라인 4행 (수량=1 + OT일대 + 월대 + OT월대)
     *  - D: 첫 행 1, 둘째 OT/h(일대), 셋째 월임대, 넷째 OT/h(월대)
     *  - F: 가격
     *  - G: 첫 행 '별도', 이후 '"'
     *  notes 있으면 비고 추가 행 (D='비고', F=내용 H 머지). */
    private int renderBody(Sheet sh, Styles s, List<DispatchedEquipment> lines, Map<Long, Equipment> eqMap, int startRow) {
        return renderBody(sh, s, lines, eqMap, startRow, null, null);
    }
    private int renderBody(Sheet sh, Styles s, List<DispatchedEquipment> lines, Map<Long, Equipment> eqMap,
                           int startRow, String fallbackCategory, String fallbackSpec) {
        // 그룹: equipment.category → list. eq 없으면 fallbackCategory.
        Map<String, List<DispatchedEquipment>> grouped = new LinkedHashMap<>();
        for (DispatchedEquipment d : lines) {
            Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
            String cat = e != null ? categoryLabel(e) : (fallbackCategory != null ? fallbackCategory : "장비");
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(d);
        }
        int r = startRow;
        for (var entry : grouped.entrySet()) {
            int groupStart = r;
            for (DispatchedEquipment d : entry.getValue()) {
                Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
                String spec = e != null ? specOf(e) : (fallbackSpec != null ? fallbackSpec : "");
                int specStart = r;
                // 원본 양식대로: D 컬럼에 수량 + 라벨 혼용, F 컬럼 가격, G 컬럼 세액, H 컬럼 비고
                Object[][] rows = {
                        {1,             money(d.getDailyPrice()),     "별도", nz(d.getDailyNote())},
                        {"OT/h (일대)", money(d.getOtDailyPrice()),   "\"", nz(d.getOtDailyNote())},
                        {"월임대",      money(d.getMonthlyPrice()),   "\"", nz(d.getMonthlyNote())},
                        {"OT/h (월대)", money(d.getOtMonthlyPrice()), "\"", nz(d.getOtMonthlyNote())},
                };
                for (Object[] row : rows) {
                    Row rw = sh.createRow(r);
                    Cell a = rw.createCell(0); a.setCellStyle(s.bodyCenter);
                    Cell b = rw.createCell(1); b.setCellStyle(s.bodyCenter);
                    Cell c = rw.createCell(2); c.setCellStyle(s.bodyCenter);
                    Cell dCell = rw.createCell(3);
                    if (row[0] instanceof Integer) dCell.setCellValue((Integer) row[0]);
                    else dCell.setCellValue((String) row[0]);
                    dCell.setCellStyle(s.bodyCenter);
                    rw.createCell(4).setCellStyle(s.bodyCenter);
                    Cell f = rw.createCell(5); f.setCellValue((String) row[1]); f.setCellStyle(s.bodyRight);
                    Cell g = rw.createCell(6); g.setCellValue((String) row[2]); g.setCellStyle(s.bodyCenter);
                    Cell h = rw.createCell(7); h.setCellValue((String) row[3]); h.setCellStyle(s.bodyLeft);
                    r++;
                }
                // 규격 4행 머지 (C 컬럼)
                sh.addMergedRegion(new CellRangeAddress(specStart, r - 1, 2, 2));
                sh.getRow(specStart).getCell(2).setCellValue(spec);

                // 비고 (notes) — 있으면 한 행 추가 (D='비고', F:H 머지 = 내용)
                if (d.getNotes() != null && !d.getNotes().isBlank()) {
                    Row rw = sh.createRow(r);
                    rw.createCell(0).setCellStyle(s.bodyCenter);
                    rw.createCell(1).setCellStyle(s.bodyCenter);
                    rw.createCell(2).setCellStyle(s.bodyCenter);
                    Cell dCell = rw.createCell(3); dCell.setCellValue("비고"); dCell.setCellStyle(s.bodyCenter);
                    rw.createCell(4).setCellStyle(s.bodyCenter);
                    Cell f = rw.createCell(5); f.setCellValue(d.getNotes()); f.setCellStyle(s.bodyLeft);
                    rw.createCell(6).setCellStyle(s.bodyCenter);
                    rw.createCell(7).setCellStyle(s.bodyLeft);
                    sh.addMergedRegion(new CellRangeAddress(r, r, 5, 7));
                    r++;
                }
            }
            // 카테고리 열(A:B) 머지 — 그룹 전체. 2행 이상일 때만.
            if (r - 1 > groupStart) {
                sh.addMergedRegion(new CellRangeAddress(groupStart, r - 1, 0, 1));
            }
            Cell catCell = sh.getRow(groupStart).getCell(0);
            catCell.setCellValue(entry.getKey());
            catCell.setCellStyle(s.categoryBig);
        }
        return r - 1;
    }

    /** 합계금액 표시 — row 10 (C11:D11 머지 영역) 에 금액 + E11 "원整" 표기. */
    private void renderTotal(Sheet sh, Styles s, List<DispatchedEquipment> lines, int totalRow, int lastBodyRow) {
        long sum = lines.stream()
                .mapToLong(d -> nzLong(d.getDailyPrice()) + nzLong(d.getOtDailyPrice())
                        + nzLong(d.getMonthlyPrice()) + nzLong(d.getOtMonthlyPrice()))
                .sum();
        Row tot = sh.getRow(totalRow);
        Cell sumVal = tot.getCell(2);
        if (sumVal == null) sumVal = tot.createCell(2);
        sumVal.setCellValue(MONEY.format(sum));
        sumVal.setCellStyle(s.sumBigValue);
    }

    private void renderFooter(Sheet sh, Styles s, int row) {
        Row r = sh.createRow(row);
        Cell c = r.createCell(0); c.setCellValue("우리는 귀사의 미래와 함께하길 원합니다.");
        c.setCellStyle(s.footerCenter);
        for (int i = 1; i <= 7; i++) r.createCell(i).setCellStyle(s.footerCenter);
        sh.addMergedRegion(new CellRangeAddress(row, row, 0, 7));
    }

    private void fillPrice(Row row, int col, Long price, Styles s) {
        Cell c = row.createCell(col);
        c.setCellValue(price != null ? MONEY.format(price) : "-");
        c.setCellStyle(s.bodyRight);
    }

    /** 원본 양식 컬럼 너비 (A=2.625, B=22.25 ... P=8.25). POI 단위 = 256 * Excel 너비. */
    private void setColumnWidths(Sheet sh) {
        double[] w = {2.625, 22.25, 12.625, 19.875, 18.75, 13.0, 12.625, 40.625, 9.0, 13.0, 3.625, 13.125, 30.0, 5.75, 11.0, 8.25};
        for (int i = 0; i < w.length; i++) {
            sh.setColumnWidth(i, (int) Math.round(w[i] * 256));
        }
        // 행 높이도 원본 대표값으로
        for (int r = 13; r < 200; r++) {
            // 본문 영역 행 높이 35.1
        }
    }

    /* ===================== Util ===================== */

    private static String safeSheetName(String name) {
        if (name == null) return "견적서";
        String safe = name.replaceAll("[\\\\/?*\\[\\]]", "_");
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    private static String categoryLabel(Equipment e) {
        return NotificationLabels.equipmentCategory(e.getCategory());
    }

    private static String specOf(Equipment e) {
        if (e.getModel() != null && !e.getModel().isBlank()) return e.getModel();
        if (e.getVehicleNo() != null) return e.getVehicleNo();
        return "";
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static long nzLong(Long v) { return v == null ? 0L : v; }

    private static String money(Long v) {
        return v == null ? "" : MONEY.format(v);
    }

    private static byte[] toBytes(XSSFWorkbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    /* ===================== Styles ===================== */

    private static class Styles {
        final CellStyle title, headerCenter, bodyCenter, bodyLeft, bodyRight, box, boxValue, totalValue, footerCenter, right;
        // 원본 양식용 추가 스타일
        final CellStyle bpCompanyBig, guiha, vertLabel, boxValueLeft, siteBig, bodyLeft12;
        final CellStyle sumLabel, sumSub, sumValue, sumWon, sumBigValue, contact, colHeader, categoryBig, dateCenter;

        Styles(XSSFWorkbook wb) {
            Font hf = wb.createFont(); hf.setBold(true); hf.setFontHeightInPoints((short) 26);
            title = wb.createCellStyle(); title.setFont(hf);
            title.setAlignment(HorizontalAlignment.CENTER); title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font hb = wb.createFont(); hb.setBold(true);
            headerCenter = wb.createCellStyle(); headerCenter.setFont(hb);
            headerCenter.setAlignment(HorizontalAlignment.CENTER); headerCenter.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(headerCenter);
            headerCenter.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerCenter.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            bodyCenter = wb.createCellStyle();
            bodyCenter.setAlignment(HorizontalAlignment.CENTER); bodyCenter.setVerticalAlignment(VerticalAlignment.CENTER);
            Font bf14 = wb.createFont(); bf14.setFontHeightInPoints((short) 14);
            bodyCenter.setFont(bf14);
            applyBorder(bodyCenter);
            bodyLeft = wb.createCellStyle();
            bodyLeft.setAlignment(HorizontalAlignment.LEFT); bodyLeft.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyLeft.setFont(bf14);
            applyBorder(bodyLeft);
            bodyRight = wb.createCellStyle();
            bodyRight.setAlignment(HorizontalAlignment.RIGHT); bodyRight.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyRight.setFont(bf14);
            applyBorder(bodyRight);

            box = wb.createCellStyle(); box.setFont(hb);
            box.setAlignment(HorizontalAlignment.CENTER); box.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(box);
            box.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            box.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            boxValue = wb.createCellStyle();
            boxValue.setAlignment(HorizontalAlignment.CENTER); boxValue.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(boxValue);

            Font tf = wb.createFont(); tf.setBold(true); tf.setFontHeightInPoints((short) 14);
            totalValue = wb.createCellStyle(); totalValue.setFont(tf);
            totalValue.setAlignment(HorizontalAlignment.CENTER); totalValue.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(totalValue);

            Font footerFont = wb.createFont(); footerFont.setBold(true); footerFont.setFontHeightInPoints((short) 16);
            footerCenter = wb.createCellStyle(); footerCenter.setFont(footerFont);
            footerCenter.setAlignment(HorizontalAlignment.CENTER); footerCenter.setVerticalAlignment(VerticalAlignment.CENTER);

            right = wb.createCellStyle();
            right.setAlignment(HorizontalAlignment.RIGHT);

            // ===== 원본 양식용 추가 스타일들 =====
            Font fBpBig = wb.createFont(); fBpBig.setBold(true); fBpBig.setFontHeightInPoints((short) 20);
            bpCompanyBig = wb.createCellStyle(); bpCompanyBig.setFont(fBpBig);
            bpCompanyBig.setAlignment(HorizontalAlignment.CENTER); bpCompanyBig.setVerticalAlignment(VerticalAlignment.CENTER);
            bpCompanyBig.setBorderBottom(BorderStyle.MEDIUM);

            Font fGuiha = wb.createFont(); fGuiha.setBold(true); fGuiha.setFontHeightInPoints((short) 16);
            guiha = wb.createCellStyle(); guiha.setFont(fGuiha);
            guiha.setAlignment(HorizontalAlignment.LEFT); guiha.setVerticalAlignment(VerticalAlignment.CENTER);
            guiha.setBorderBottom(BorderStyle.MEDIUM);

            vertLabel = wb.createCellStyle(); vertLabel.setFont(hb);
            vertLabel.setAlignment(HorizontalAlignment.CENTER); vertLabel.setVerticalAlignment(VerticalAlignment.CENTER);
            vertLabel.setRotation((short) 255); // POI: -90도 (세로 텍스트)
            applyBorder(vertLabel);
            vertLabel.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            vertLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            boxValueLeft = wb.createCellStyle();
            boxValueLeft.setAlignment(HorizontalAlignment.LEFT); boxValueLeft.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(boxValueLeft);

            Font fSiteBig = wb.createFont(); fSiteBig.setBold(true); fSiteBig.setFontHeightInPoints((short) 14);
            siteBig = wb.createCellStyle(); siteBig.setFont(fSiteBig);
            siteBig.setAlignment(HorizontalAlignment.CENTER); siteBig.setVerticalAlignment(VerticalAlignment.CENTER);

            Font fBody12 = wb.createFont(); fBody12.setFontHeightInPoints((short) 12);
            bodyLeft12 = wb.createCellStyle(); bodyLeft12.setFont(fBody12);
            bodyLeft12.setAlignment(HorizontalAlignment.LEFT); bodyLeft12.setVerticalAlignment(VerticalAlignment.CENTER);

            Font fSumLabel = wb.createFont(); fSumLabel.setBold(true); fSumLabel.setFontHeightInPoints((short) 14);
            sumLabel = wb.createCellStyle(); sumLabel.setFont(fSumLabel);
            sumLabel.setAlignment(HorizontalAlignment.CENTER); sumLabel.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(sumLabel);

            Font fSumSub = wb.createFont(); fSumSub.setFontHeightInPoints((short) 14);
            sumSub = wb.createCellStyle(); sumSub.setFont(fSumSub);
            sumSub.setAlignment(HorizontalAlignment.CENTER); sumSub.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(sumSub);

            sumValue = wb.createCellStyle();
            sumValue.setAlignment(HorizontalAlignment.CENTER); sumValue.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(sumValue);

            sumWon = wb.createCellStyle(); sumWon.setFont(fSumLabel);
            sumWon.setAlignment(HorizontalAlignment.RIGHT); sumWon.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(sumWon);

            Font fSumBig = wb.createFont(); fSumBig.setBold(true); fSumBig.setFontHeightInPoints((short) 18);
            sumBigValue = wb.createCellStyle(); sumBigValue.setFont(fSumBig);
            sumBigValue.setAlignment(HorizontalAlignment.RIGHT); sumBigValue.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(sumBigValue);

            Font fContact = wb.createFont(); fContact.setFontHeightInPoints((short) 14);
            contact = wb.createCellStyle(); contact.setFont(fContact);
            contact.setAlignment(HorizontalAlignment.LEFT); contact.setVerticalAlignment(VerticalAlignment.CENTER);

            Font fColHdr = wb.createFont(); fColHdr.setBold(true); fColHdr.setFontHeightInPoints((short) 12);
            colHeader = wb.createCellStyle(); colHeader.setFont(fColHdr);
            colHeader.setAlignment(HorizontalAlignment.CENTER); colHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(colHeader);
            colHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            colHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font fCatBig = wb.createFont(); fCatBig.setFontHeightInPoints((short) 14);
            categoryBig = wb.createCellStyle(); categoryBig.setFont(fCatBig);
            categoryBig.setAlignment(HorizontalAlignment.CENTER); categoryBig.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorder(categoryBig);

            Font fDate = wb.createFont(); fDate.setFontHeightInPoints((short) 16);
            dateCenter = wb.createCellStyle(); dateCenter.setFont(fDate);
            dateCenter.setAlignment(HorizontalAlignment.CENTER); dateCenter.setVerticalAlignment(VerticalAlignment.CENTER);
        }
        private static void applyBorder(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN); s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
        }
    }
}
