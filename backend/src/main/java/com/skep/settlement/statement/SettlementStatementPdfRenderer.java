package com.skep.settlement.statement;

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
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.skep.settlement.dto.SettlementDtos.OwnerSettlement;
import com.skep.settlement.dto.SettlementDtos.SettlementItem;
import com.skep.settlement.dto.SettlementDtos.SettlementSummaryResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 거래내역서 PDF (iText7). QuotationPdfService 표 패턴 재사용.
 * SettlementService.summary 가 계산한 숫자(소유자별 items·소계·전체합계)를 렌더만 함 — 금액 재계산·프로레이션 없음.
 */
@Service
public class SettlementStatementPdfRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] render(String companyName, LocalDate from, LocalDate to, SettlementSummaryResponse data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            PdfFont regular = loadFont("fonts/NanumGothic.ttf");
            PdfFont bold = loadFont("fonts/NanumGothicBold.ttf");
            doc.setFont(regular);
            doc.setFontSize(10);

            doc.add(new Paragraph("거 래 내 역 서")
                    .setFont(bold).setFontSize(22).setTextAlignment(TextAlignment.CENTER).setMarginBottom(14));

            // 헤더 — 상호 / 발행일 / 기간
            Table header = new Table(UnitValue.createPercentArray(new float[]{16, 44, 16, 24}))
                    .useAllAvailableWidth().setMarginBottom(6);
            header.addCell(headerCell("상호", bold));
            header.addCell(valueCell(companyName));
            header.addCell(headerCell("발행일", bold));
            header.addCell(valueCell(LocalDate.now().format(DATE_FMT)));
            header.addCell(headerCell("기간", bold));
            header.addCell(new Cell(1, 3).add(new Paragraph(formatPeriod(from, to)))
                    .setBorder(new SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)).setPadding(4));
            doc.add(header);

            doc.add(new Paragraph(
                    "산정기준: 근무일수 기준(월대÷25×근무일수+OT, 일대×근무일수+OT), 계약기간 기준 집계. 수수료 미표기.")
                    .setFontSize(8).setFontColor(ColorConstants.DARK_GRAY).setMarginBottom(12));

            for (OwnerSettlement owner : data.owners()) {
                doc.add(new Paragraph(owner.ownerCompanyName() + (owner.isSelf() ? " (본인)" : " (협력사)"))
                        .setFont(bold).setFontSize(12).setMarginBottom(4));

                Table t = new Table(UnitValue.createPercentArray(new float[]{22, 22, 20, 12, 10, 14}))
                        .useAllAvailableWidth();
                t.addHeaderCell(tableHeader("자원", bold));
                t.addHeaderCell(tableHeader("현장", bold));
                t.addHeaderCell(tableHeader("단가", bold));
                t.addHeaderCell(tableHeader("근무일수", bold));
                t.addHeaderCell(tableHeader("OT", bold));
                t.addHeaderCell(tableHeader("금액(원)", bold));

                for (SettlementItem it : owner.items()) {
                    t.addCell(tableCell(resourceLabel(it)));
                    t.addCell(tableCell(it.siteName() != null ? it.siteName() : "-"));
                    t.addCell(tableCell(rateLabel(it)));
                    t.addCell(tableCellRight(numOrDash(effWorkDays(it))));
                    t.addCell(tableCellRight(numOrDash(effOtDays(it))));
                    t.addCell(tableCellRight(it.amount() != null ? money(it.amount()) : "미입력"));
                }

                Table subtotal = new Table(UnitValue.createPercentArray(new float[]{86, 14}))
                        .useAllAvailableWidth().setMarginTop(2).setMarginBottom(12);
                subtotal.addCell(totalLabelCell("소계 (" + owner.itemCount() + "건)", bold));
                subtotal.addCell(tableCellRight(money(owner.totalAmount())));
                doc.add(t);
                doc.add(subtotal);
            }

            Table grand = new Table(UnitValue.createPercentArray(new float[]{86, 14}))
                    .useAllAvailableWidth().setMarginTop(4);
            grand.addCell(totalLabelCell("전체 합계", bold));
            grand.addCell(tableCellRight(money(data.grandTotal())));
            doc.add(grand);

            doc.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("거래내역서 PDF 생성 실패", e);
        }
    }

    private static String resourceLabel(SettlementItem it) {
        String kind = "EQUIPMENT".equals(it.resourceType()) ? "장비" : "인원";
        return kind + " · " + it.resourceLabel();
    }

    private static String rateLabel(SettlementItem it) {
        if ("MONTHLY".equals(it.amountBasis())) return "월대 " + money(it.monthlyPrice());
        if ("DAILY".equals(it.amountBasis())) return "일대 " + money(it.dailyPrice());
        return "-";
    }

    /** 금액 산정에 실제 쓰인 근무일수 = 수동 입력 우선, 없으면 자동 집계값(summary 와 동일 규칙, 재계산 아님). */
    private static Integer effWorkDays(SettlementItem it) {
        return it.settlementWorkDays() != null ? it.settlementWorkDays() : it.derivedWorkDays();
    }

    private static Integer effOtDays(SettlementItem it) {
        return it.settlementWorkDays() != null ? it.settlementOtDays() : it.derivedOtDays();
    }

    private static String numOrDash(Integer v) {
        return v == null ? "-" : String.valueOf(v);
    }

    private static String money(Long v) {
        return v == null ? "-" : String.format("%,d", v);
    }

    private static String formatPeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "전체 기간";
        return (from != null ? from.format(DATE_FMT) : "처음") + " ~ " + (to != null ? to.format(DATE_FMT) : "현재");
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
}
