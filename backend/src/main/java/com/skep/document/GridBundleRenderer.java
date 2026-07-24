package com.skep.document;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.skep.collection.PdfMergeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 심사 제출용 "관련서류 그리드" PDF 렌더러 — 자원(장비/조종원)마다 1페이지에
 * 상단 제목(자원 라벨) + 2열 그리드(각 칸 = 서류 항목명 + 서류 사진). 없는 필수 서류 칸은 "미제출".
 * PDF 서류는 PDFBox 로 첫 페이지를 이미지로 렌더, 이미지 서류는 그대로 셀에 맞춰 넣는다.
 *
 * 낱장 병합(PdfMergeService)·개별 조회·ZIP 과는 별개로 "병합 PDF" 출력에만 쓰인다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GridBundleRenderer {

    private final PdfMergeService pdfMerge;

    /** 셀 이미지 배치 박스(pt) — A4 여백 30, 2열 기준. */
    private static final float IMG_BOX_W = 245f;
    private static final float IMG_BOX_H = 320f;

    private static final Color TITLE_BG = new DeviceRgb(15, 23, 42);      // slate-900
    private static final Color CELL_BORDER = new DeviceRgb(203, 213, 225); // slate-300
    private static final Color MISSING_BG = new DeviceRgb(243, 244, 246);  // gray-100
    private static final Color GRAY = new DeviceRgb(100, 116, 139);        // slate-500
    private static final Color STAMP_BG = new DeviceRgb(5, 150, 105);      // emerald-600

    /** Nanum 폰트 바이트 캐시 — 자원마다 재로드하지 않게. */
    private volatile byte[] fontBytes;

    /** 그리드 셀 1칸. present=false(미제출)면 rawBytes/stamp 무시. */
    public record GridCell(String heading, byte[] rawBytes, String contentType,
                           String stampBadge, String stampSub, boolean missing) {}

    /** 자원 1건 = 제목 + 셀 목록. 렌더 시 자원마다 새 페이지. */
    public record ResourceSection(String title, List<GridCell> cells) {}

    public byte[] render(List<ResourceSection> sections) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdf, PageSize.A4)) {
            doc.setMargins(30, 30, 30, 30);
            PdfFont font = koreanFont();
            doc.setFont(font);

            boolean first = true;
            boolean anyContent = false;
            for (ResourceSection sec : sections) {
                if (sec == null || sec.cells() == null || sec.cells().isEmpty()) continue;
                if (!first) doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                first = false;
                anyContent = true;

                doc.add(new Paragraph(sec.title() == null ? "" : sec.title())
                        .setFontColor(ColorConstants.WHITE).setBackgroundColor(TITLE_BG)
                        .setBold().setFontSize(15).setPadding(8).setMarginBottom(1));
                doc.add(new Paragraph("관련서류 (심사 제출용)")
                        .setFontColor(GRAY).setFontSize(9).setMarginTop(0).setMarginBottom(6));

                Table table = new Table(UnitValue.createPercentArray(new float[]{1f, 1f}))
                        .useAllAvailableWidth();
                for (GridCell c : sec.cells()) table.addCell(buildCell(c, font));
                if (sec.cells().size() % 2 == 1) {
                    table.addCell(new Cell().setBorder(new SolidBorder(CELL_BORDER, 0.8f)));
                }
                doc.add(table);
            }
            if (!anyContent) doc.add(new Paragraph(" "));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("관련서류 그리드 PDF 생성 실패: " + e.getMessage(), e);
        }
    }

    private Cell buildCell(GridCell c, PdfFont font) {
        Cell cell = new Cell().setPadding(6).setMinHeight(200)
                .setBorder(new SolidBorder(CELL_BORDER, 0.8f));
        cell.add(new Paragraph(c.heading() == null ? "" : c.heading())
                .setBold().setFontSize(10).setMarginBottom(4));

        if (c.missing()) {
            cell.setBackgroundColor(MISSING_BG);
            cell.add(new Paragraph("미제출").setFontColor(GRAY).setFontSize(13)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(60).setMarginBottom(60));
            return cell;
        }

        Image img = gridImage(c.rawBytes(), c.contentType());
        boolean previewOk = img != null;
        if (img != null) {
            img.setHorizontalAlignment(HorizontalAlignment.CENTER);
            cell.add(img);
        } else {
            cell.add(new Paragraph("원본 파일 별첨 · 미리보기 불가").setFontColor(GRAY).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(50).setMarginBottom(50));
        }
        // 사진이 실제로 렌더된 셀에만 진위 스탬프 — 미리보기 불가 칸에 도장만 찍혀 공신력이 어긋나 보이는 것 방지.
        if (previewOk && c.stampBadge() != null) {
            cell.add(new Paragraph(c.stampBadge())
                    .setFontColor(ColorConstants.WHITE).setBackgroundColor(STAMP_BG)
                    .setFontSize(8.5f).setPaddingLeft(4).setPaddingRight(4)
                    .setPaddingTop(1).setPaddingBottom(1).setMarginTop(4));
        }
        if (previewOk && c.stampSub() != null) {
            cell.add(new Paragraph(c.stampSub()).setFontColor(GRAY).setFontSize(7.5f).setMarginTop(1));
        }
        return cell;
    }

    /** 서류 바이트 → 셀에 맞춘 iText Image. 이미지는 그대로, PDF/비표준 이미지는 첫 페이지를 렌더. 실패 시 null. */
    private Image gridImage(byte[] raw, String contentType) {
        if (raw == null || raw.length == 0) return null;
        String ct = contentType == null ? "" : contentType.toLowerCase();
        // 1) 표준 이미지 — iText 로 바로(라운드트립 없이, 최상 화질).
        if (ct.startsWith("image/")) {
            try {
                return scaled(new Image(ImageDataFactory.create(raw)));
            } catch (Exception ignore) {
                // HEIC/WEBP 등 iText 미지원 — 아래 PDF 경유 렌더로.
            }
        }
        // 2) PDF(또는 위에서 실패한 이미지) → PDF 정규화 후 첫 페이지 렌더.
        byte[] pdfBytes = ct.contains("pdf") ? raw : pdfMerge.singleToPdf(raw, ct);
        if (pdfBytes == null) return null;
        byte[] png = firstPagePng(pdfBytes);
        if (png == null) return null;
        try {
            return scaled(new Image(ImageDataFactory.create(png)));
        } catch (Exception e) {
            return null;
        }
    }

    private Image scaled(Image img) {
        img.scaleToFit(IMG_BOX_W, IMG_BOX_H);
        return img;
    }

    /** PDF 첫 페이지 → PNG 바이트. 실패(암호화/손상 등) 시 null. */
    private byte[] firstPagePng(byte[] pdfBytes) {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) return null;
            BufferedImage bi = new PDFRenderer(doc).renderImageWithDPI(0, 110, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("그리드 PDF 첫 페이지 렌더 실패: {}", e.getMessage());
            return null;
        }
    }

    private PdfFont koreanFont() throws java.io.IOException {
        if (fontBytes == null) {
            try (var in = new ClassPathResource("fonts/NanumGothicBold.ttf").getInputStream()) {
                fontBytes = in.readAllBytes();
            }
        }
        return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
    }
}
