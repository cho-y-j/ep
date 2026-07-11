package com.skep.settlement.statement;

import com.skep.settlement.dto.SettlementDtos.OwnerSettlement;
import com.skep.settlement.dto.SettlementDtos.SettlementItem;
import com.skep.settlement.dto.SettlementDtos.SettlementSummaryResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 거래내역서 .xlsx (POI). QuotationExcelService POI 패턴 재사용. 단일 시트에 소유자별 구역 + 전체 합계.
 * SettlementService.summary 가 계산한 숫자를 렌더만 함 — 금액 재계산·프로레이션 없음.
 */
@Service
public class SettlementStatementXlsxRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] COLS = {"자원", "현장", "단가", "근무일수", "OT일수", "금액(원)"};

    public byte[] render(String companyName, LocalDate from, LocalDate to, SettlementSummaryResponse data) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("거래내역서");
            Styles s = new Styles(wb);
            int r = 0;

            cell(sh, r, 0, "거 래 내 역 서", s.title);
            sh.addMergedRegion(new CellRangeAddress(r, r, 0, COLS.length - 1));
            r++;
            cell(sh, r++, 0, "상호: " + companyName, s.info);
            cell(sh, r++, 0, "기간: " + formatPeriod(from, to), s.info);
            cell(sh, r++, 0, "발행일: " + LocalDate.now().format(DATE_FMT), s.info);
            cell(sh, r, 0, "산정기준: 근무일수 기준(월대÷25×근무일수+OT, 일대×근무일수+OT), 계약기간 기준 집계. 수수료 미표기.", s.info);
            sh.addMergedRegion(new CellRangeAddress(r, r, 0, COLS.length - 1));
            r += 2;

            for (OwnerSettlement owner : data.owners()) {
                cell(sh, r, 0, owner.ownerCompanyName() + (owner.isSelf() ? " (본인)" : " (협력사)"), s.section);
                sh.addMergedRegion(new CellRangeAddress(r, r, 0, COLS.length - 1));
                r++;

                Row head = sh.createRow(r++);
                for (int c = 0; c < COLS.length; c++) cellIn(head, c, COLS[c], s.header);

                for (SettlementItem it : owner.items()) {
                    Row row = sh.createRow(r++);
                    cellIn(row, 0, resourceLabel(it), s.body);
                    cellIn(row, 1, it.siteName() != null ? it.siteName() : "-", s.body);
                    cellIn(row, 2, rateLabel(it), s.body);
                    numCell(row, 3, effWorkDays(it), s);
                    numCell(row, 4, effOtDays(it), s);
                    if (it.amount() != null) {
                        Cell amt = row.createCell(5);
                        amt.setCellValue(it.amount());
                        amt.setCellStyle(s.money);
                    } else {
                        cellIn(row, 5, "미입력", s.bodyRight);
                    }
                }

                Row sub = sh.createRow(r++);
                cellIn(sub, 0, "소계 (" + owner.itemCount() + "건)", s.totalLabel);
                for (int c = 1; c < 5; c++) cellIn(sub, c, "", s.totalLabel);
                sh.addMergedRegion(new CellRangeAddress(sub.getRowNum(), sub.getRowNum(), 0, 4));
                Cell subVal = sub.createCell(5);
                subVal.setCellValue(owner.totalAmount());
                subVal.setCellStyle(s.moneyTotal);
                r++;
            }

            Row grand = sh.createRow(r);
            cellIn(grand, 0, "전체 합계", s.totalLabel);
            for (int c = 1; c < 5; c++) cellIn(grand, c, "", s.totalLabel);
            sh.addMergedRegion(new CellRangeAddress(r, r, 0, 4));
            Cell grandVal = grand.createCell(5);
            grandVal.setCellValue(data.grandTotal());
            grandVal.setCellStyle(s.moneyTotal);

            int[] widths = {26, 22, 20, 12, 10, 16};
            for (int c = 0; c < widths.length; c++) sh.setColumnWidth(c, widths[c] * 256);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("거래내역서 xlsx 생성 실패", e);
        }
    }

    private static void numCell(Row row, int col, Integer v, Styles s) {
        Cell c = row.createCell(col);
        if (v == null) { c.setCellValue("-"); c.setCellStyle(s.bodyRight); }
        else { c.setCellValue(v); c.setCellStyle(s.bodyRight); }
    }

    private static Cell cell(Sheet sh, int r, int col, String text, CellStyle style) {
        Row row = sh.getRow(r) != null ? sh.getRow(r) : sh.createRow(r);
        return cellIn(row, col, text, style);
    }

    private static Cell cellIn(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
        return c;
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

    private static String money(Long v) {
        return v == null ? "-" : String.format("%,d", v);
    }

    private static String formatPeriod(LocalDate from, LocalDate to) {
        if (from == null && to == null) return "전체 기간";
        return (from != null ? from.format(DATE_FMT) : "처음") + " ~ " + (to != null ? to.format(DATE_FMT) : "현재");
    }

    private static class Styles {
        final CellStyle title, info, section, header, body, bodyRight, money, totalLabel, moneyTotal;

        Styles(XSSFWorkbook wb) {
            Font tf = wb.createFont(); tf.setBold(true); tf.setFontHeightInPoints((short) 18);
            title = wb.createCellStyle(); title.setFont(tf);
            title.setAlignment(HorizontalAlignment.CENTER);

            info = wb.createCellStyle();
            info.setAlignment(HorizontalAlignment.LEFT);

            Font sf = wb.createFont(); sf.setBold(true); sf.setFontHeightInPoints((short) 12);
            section = wb.createCellStyle(); section.setFont(sf);
            section.setAlignment(HorizontalAlignment.LEFT);

            Font hf = wb.createFont(); hf.setBold(true);
            header = wb.createCellStyle(); header.setFont(hf);
            header.setAlignment(HorizontalAlignment.CENTER);
            border(header);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            body = wb.createCellStyle();
            body.setAlignment(HorizontalAlignment.LEFT);
            border(body);

            bodyRight = wb.createCellStyle();
            bodyRight.setAlignment(HorizontalAlignment.RIGHT);
            border(bodyRight);

            money = wb.createCellStyle();
            money.setAlignment(HorizontalAlignment.RIGHT);
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            border(money);

            totalLabel = wb.createCellStyle(); totalLabel.setFont(hf);
            totalLabel.setAlignment(HorizontalAlignment.RIGHT);
            border(totalLabel);

            moneyTotal = wb.createCellStyle(); moneyTotal.setFont(hf);
            moneyTotal.setAlignment(HorizontalAlignment.RIGHT);
            moneyTotal.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            border(moneyTotal);
        }

        private static void border(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN); s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN); s.setBorderRight(BorderStyle.THIN);
        }
    }
}
