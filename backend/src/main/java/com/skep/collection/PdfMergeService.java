package com.skep.collection;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 업로드된 서류(PDF/이미지)를 받은 순서대로 하나의 PDF로 병합. */
@Service
public class PdfMergeService {

    private static final Logger log = LoggerFactory.getLogger(PdfMergeService.class);

    /** 병합 대상 1건 — 파일 바이트 + content-type. PDF 와 이미지(PNG/JPEG/GIF + HEIC/WEBP는 LibreOffice 폴백)만 처리. */
    public record Part(byte[] bytes, String contentType, String label) {}

    public byte[] merge(List<Part> parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument dest = new PdfDocument(new PdfWriter(out));
        PdfMerger merger = new PdfMerger(dest);
        int merged = 0;
        for (Part p : parts) {
            if (p == null || p.bytes() == null || p.bytes().length == 0) continue;
            String ct = p.contentType() == null ? "" : p.contentType().toLowerCase();
            try {
                byte[] pdfBytes;
                if (ct.contains("pdf")) {
                    pdfBytes = p.bytes();
                } else if (ct.startsWith("image/")) {
                    pdfBytes = imageToPdf(p.bytes(), ct);
                } else {
                    log.info("PDF 병합 건너뜀 (지원 안 함): {} ({})", p.label(), ct);
                    continue;
                }
                try (PdfDocument src = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)))) {
                    merger.merge(src, 1, src.getNumberOfPages());
                }
                merged++;
            } catch (Exception e) {
                log.warn("PDF 병합 실패, 건너뜀: {} — {}", p.label(), e.getMessage());
            }
        }
        dest.close();
        if (merged == 0) {
            throw new IllegalStateException("병합할 수 있는 서류가 없습니다 (PDF/이미지만 가능)");
        }
        return out.toByteArray();
    }

    /** 이미지 1장 → PDF. iText 가 못 읽는 포맷(HEIC/WEBP 등)은 LibreOffice 로 폴백 변환. */
    private byte[] imageToPdf(byte[] imageBytes, String contentType) throws Exception {
        try {
            return imageToPdfIText(imageBytes);
        } catch (Exception itextFail) {
            log.info("iText 이미지 디코드 실패 → LibreOffice 폴백 ({})", contentType);
            return imageToPdfViaLibreOffice(imageBytes, extFromCt(contentType));
        }
    }

    /** iText 로 이미지를 A4 한 페이지에 맞춰 PDF로 (PNG/JPEG/GIF/BMP/TIFF). */
    private byte[] imageToPdfIText(byte[] imageBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(out))) {
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(18, 18, 18, 18);
            Image img = new Image(ImageDataFactory.create(imageBytes));
            img.setAutoScale(true);
            doc.add(img);
            doc.close();
        }
        return out.toByteArray();
    }

    /** LibreOffice headless 로 이미지(HEIC/WEBP 등)를 PDF로 변환. */
    private byte[] imageToPdfViaLibreOffice(byte[] imageBytes, String ext) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("skep-imgpdf-");
        try {
            Path imgFile = workDir.resolve("img" + ext);
            Files.write(imgFile, imageBytes);
            String userProfile = "-env:UserInstallation=file://" + workDir.resolve("lo-profile");
            ProcessBuilder pb = new ProcessBuilder(
                    "libreoffice", userProfile, "--headless", "--nologo", "--nofirststartwizard",
                    "--convert-to", "pdf", "--outdir", workDir.toString(), imgFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("LibreOffice 이미지 변환 타임아웃");
            }
            if (p.exitValue() != 0) {
                throw new IOException("LibreOffice 이미지 변환 실패 (code=" + p.exitValue() + "): " + output);
            }
            return Files.readAllBytes(workDir.resolve("img.pdf"));
        } finally {
            try (var stream = Files.walk(workDir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) { } });
            } catch (IOException ignored) { /* best-effort cleanup */ }
        }
    }

    private static String extFromCt(String ct) {
        if (ct == null) return ".img";
        if (ct.contains("heic") || ct.contains("heif")) return ".heic";
        if (ct.contains("webp")) return ".webp";
        if (ct.contains("png")) return ".png";
        if (ct.contains("gif")) return ".gif";
        if (ct.contains("bmp")) return ".bmp";
        if (ct.contains("tif")) return ".tiff";
        return ".jpg";
    }
}
