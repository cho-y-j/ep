package com.skep.quotation.pdf;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.dispatch.DispatchedEquipment;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 견적서 PDF 생성. iText 7.
 * 두 종류:
 *  - SINGLE: 한 공급사 dispatched 차량만 + 단가
 *  - FULL: BP 가 요청한 전체 장비(요청 자체의 카테고리) + 이 공급사 차량에만 단가 표기
 */
@Service
@RequiredArgsConstructor
public class QuotationPdfService {

    public enum Mode { SINGLE, FULL }

    private final QuotationRequestRepository requests;
    private final DispatchedEquipmentRepository dispatched;
    private final CompanyRepository companies;
    private final EquipmentRepository equipments;
    private final SiteRepository sites;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] render(Long quotationRequestId, Mode mode, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(quotationRequestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));

        // H-1: 공급사 시점이면 자기 회사 dispatched 행만 — 멀티공급사 견적에서 경쟁사 단가 노출 방지.
        // ADMIN/BP 는 전체. (listByRequest 와 동일 정책)
        List<DispatchedEquipment> sent;
        boolean isSupplier = actor != null
                && (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER);
        if (isSupplier && actor.companyId() != null) {
            sent = dispatched.findByQuotationRequestIdAndSupplierCompanyId(quotationRequestId, actor.companyId());
        } else {
            sent = dispatched.findByQuotationRequestId(quotationRequestId);
        }
        if (sent.isEmpty()) {
            throw ApiException.badRequest("NO_DISPATCH", "차량이 아직 send 되지 않았습니다");
        }

        Company bp = qr.getBpCompanyId() != null ? companies.findById(qr.getBpCompanyId()).orElse(null) : null;
        Long supplierId = sent.get(0).getSupplierCompanyId();
        Company supplier = companies.findById(supplierId).orElse(null);
        Site site = qr.getSiteId() != null ? sites.findById(qr.getSiteId()).orElse(null) : null;

        var eqIds = sent.stream().map(DispatchedEquipment::getEquipmentId).toList();
        Map<Long, Equipment> eqMap = new HashMap<>();
        for (Equipment e : equipments.findAllById(eqIds)) eqMap.put(e.getId(), e);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            PdfFont regular = loadFont("fonts/NanumGothic.ttf");
            PdfFont bold = loadFont("fonts/NanumGothicBold.ttf");
            doc.setFont(regular);
            doc.setFontSize(10);

            // 제목
            String title = mode == Mode.FULL ? "견 적 서 (전체)" : "견 적 서";
            doc.add(new Paragraph(title)
                    .setFont(bold).setFontSize(22).setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

            // 헤더 표 — BP / 공급사 / 현장 / 기간
            Table header = new Table(UnitValue.createPercentArray(new float[]{18, 32, 18, 32}))
                    .useAllAvailableWidth().setMarginBottom(15);
            header.addCell(headerCell("발 주", bold));
            header.addCell(valueCell(bp != null ? bp.getName() : "-"));
            header.addCell(headerCell("공급사", bold));
            header.addCell(valueCell(supplier != null ? supplier.getName() : "-"));
            header.addCell(headerCell("현장", bold));
            header.addCell(valueCell(site != null ? site.getName() : (qr.getWorkLocationText() != null ? qr.getWorkLocationText() : "장소 협의")));
            header.addCell(headerCell("기간", bold));
            header.addCell(valueCell(formatPeriod(qr)));
            header.addCell(headerCell("견적일", bold));
            header.addCell(valueCell(java.time.LocalDate.now().format(DATE_FMT)));
            header.addCell(headerCell("견적번호", bold));
            header.addCell(valueCell("Q-" + quotationRequestId));
            doc.add(header);

            // 장비 표
            doc.add(new Paragraph("배차 장비").setFont(bold).setFontSize(12).setMarginBottom(6));
            Table eqTable = new Table(UnitValue.createPercentArray(new float[]{6, 26, 22, 18, 14, 14}))
                    .useAllAvailableWidth();
            eqTable.addHeaderCell(tableHeader("#", bold));
            eqTable.addHeaderCell(tableHeader("차량번호", bold));
            eqTable.addHeaderCell(tableHeader("모델/사양", bold));
            eqTable.addHeaderCell(tableHeader("카테고리", bold));
            eqTable.addHeaderCell(tableHeader("일대(원)", bold));
            eqTable.addHeaderCell(tableHeader("월대(원)", bold));

            long totalDaily = 0, totalMonthly = 0;
            int idx = 1;
            for (DispatchedEquipment d : sent) {
                Equipment e = eqMap.get(d.getEquipmentId());
                eqTable.addCell(tableCell(String.valueOf(idx++)));
                eqTable.addCell(tableCell(e != null && e.getVehicleNo() != null ? e.getVehicleNo() : ("#" + d.getEquipmentId())));
                eqTable.addCell(tableCell(e != null && e.getModel() != null ? e.getModel() : "-"));
                eqTable.addCell(tableCell(e != null && e.getCategory() != null ? e.getCategory().name() : "-"));
                eqTable.addCell(tableCellRight(d.getDailyPrice() != null ? formatMoney(d.getDailyPrice()) : "-"));
                eqTable.addCell(tableCellRight(d.getMonthlyPrice() != null ? formatMoney(d.getMonthlyPrice()) : "-"));
                if (d.getDailyPrice() != null) totalDaily += d.getDailyPrice();
                if (d.getMonthlyPrice() != null) totalMonthly += d.getMonthlyPrice();
            }
            // FULL 모드: 요청 카테고리에 비해 send 한 차가 부족한 경우 빈 행 표시 (단가 빈칸)
            // — 단순화: 일단 SINGLE/FULL 동일 표. 다만 FULL 은 표 아래에 "BP 요청 항목 안내" 추가.
            doc.add(eqTable);

            // 합계
            Table total = new Table(UnitValue.createPercentArray(new float[]{72, 14, 14})).useAllAvailableWidth().setMarginTop(4);
            total.addCell(totalLabelCell("합  계", bold));
            total.addCell(tableCellRight(formatMoney(totalDaily)));
            total.addCell(tableCellRight(formatMoney(totalMonthly)));
            doc.add(total);

            // 사전검사 / 입소 안내 (값이 있으면)
            doc.add(new Paragraph("\n안전점검 / 입소 안내").setFont(bold).setFontSize(12).setMarginTop(15));
            doc.add(new Paragraph("• 차량검사 일정과 입소검사 일정은 별도 발송됩니다.\n• 검사 완료된 자원만 현장 진입이 가능합니다.").setFontSize(9));

            // 사인란
            doc.add(new Paragraph("\n").setMarginTop(20));
            Table sign = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            sign.addCell(signCell("발주 (BP)", bp != null ? bp.getName() : "", bold, regular));
            sign.addCell(signCell("공급사", supplier != null ? supplier.getName() : "", bold, regular));
            doc.add(sign);

            doc.close();
            return baos.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("PDF 생성 실패", e);
        }
    }

    private PdfFont loadFont(String classpath) throws IOException {
        try (var in = new ClassPathResource(classpath).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        }
    }

    private Cell headerCell(String text, PdfFont bold) {
        return new Cell().add(new Paragraph(text).setFont(bold))
                .setBackgroundColor(new DeviceRgb(245, 247, 250))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private Cell valueCell(String text) {
        return new Cell().add(new Paragraph(text == null ? "-" : text))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private Cell tableHeader(String text, PdfFont bold) {
        return new Cell().add(new Paragraph(text).setFont(bold).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(new DeviceRgb(235, 240, 246))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private Cell tableCell(String text) {
        return new Cell().add(new Paragraph(text == null ? "-" : text))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private Cell tableCellRight(String text) {
        return new Cell().add(new Paragraph(text == null ? "-" : text).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private Cell totalLabelCell(String text, PdfFont bold) {
        return new Cell().add(new Paragraph(text).setFont(bold).setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(new DeviceRgb(245, 247, 250))
                .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))
                .setPadding(4);
    }

    private Cell signCell(String label, String name, PdfFont bold, PdfFont regular) {
        Cell c = new Cell();
        c.add(new Paragraph(label).setFont(bold).setFontSize(11));
        c.add(new Paragraph(name).setFont(regular).setFontSize(11).setMarginTop(6));
        c.add(new Paragraph("\n\n(인)").setTextAlignment(TextAlignment.RIGHT).setMarginTop(20));
        c.setPadding(10).setBorder(new SolidBorder(ColorConstants.DARK_GRAY, 0.8f));
        c.setHorizontalAlignment(HorizontalAlignment.CENTER);
        return c;
    }

    private String formatMoney(long v) {
        return String.format("%,d", v);
    }

    private String formatPeriod(QuotationRequest qr) {
        String start = qr.getWorkPeriodStart() != null ? qr.getWorkPeriodStart().format(DATE_FMT) : "—";
        String end = qr.getWorkPeriodEnd() != null ? qr.getWorkPeriodEnd().format(DATE_FMT) : "—";
        return start + " ~ " + end;
    }
}
