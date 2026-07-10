package com.skep.worksheet;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * S-9-C: 작업계획서 DOCX → PDF 변환 (LibreOffice headless) + 이메일 발송.
 * skep 원본 services/document-service/WorksheetMailService 를 v2 패키지로 이식.
 */
@Service
public class WorksheetMailService {

    private static final Logger log = LoggerFactory.getLogger(WorksheetMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${MAIL_USERNAME:}")
    private String defaultFrom;

    /** PDF 출력 옵션 — 이미지 압축 (네이버/지메일 첨부 한도 고려). */
    private static final String PDF_EXPORT_FILTER = "pdf:writer_pdf_Export:"
            + "{\"ReduceImageResolution\":{\"type\":\"boolean\",\"value\":\"true\"},"
            + "\"MaxImageResolution\":{\"type\":\"long\",\"value\":\"150\"},"
            + "\"UseLosslessCompression\":{\"type\":\"boolean\",\"value\":\"false\"},"
            + "\"Quality\":{\"type\":\"long\",\"value\":\"80\"}}";

    public WorksheetMailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * DOCX 바이트를 LibreOffice headless 로 PDF 변환.
     * UserInstallation 을 요청별 임시 디렉토리로 분리 — 동시 실행 시 기본 프로파일 락 충돌 방지.
     */
    public byte[] convertDocxToPdf(byte[] docxBytes, String baseName) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("skep-pdf-");
        try {
            String safeName = (baseName == null || baseName.isBlank()) ? "worksheet"
                    : baseName.replaceAll("[^\\p{L}\\p{N}_-]", "_");
            Path docxFile = workDir.resolve(safeName + ".docx");
            Files.write(docxFile, docxBytes);

            String userProfile = "-env:UserInstallation=file://" + workDir.resolve("lo-profile");
            ProcessBuilder pb = new ProcessBuilder(
                    "libreoffice", userProfile, "--headless", "--nologo", "--nofirststartwizard",
                    "--convert-to", PDF_EXPORT_FILTER, "--outdir", workDir.toString(), docxFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(90, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("LibreOffice 변환 타임아웃");
            }
            if (p.exitValue() != 0) {
                throw new IOException("LibreOffice 변환 실패 (code=" + p.exitValue() + "): " + output);
            }
            Path pdfFile = workDir.resolve(safeName + ".pdf");
            return Files.readAllBytes(pdfFile);
        } finally {
            try (var stream = Files.walk(workDir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) { } });
            } catch (IOException ignored) {
                /* best-effort cleanup */
            }
        }
    }

    /** HTML 바이트를 openhtmltopdf 로 PDF 변환. XHTML 형식 필수 (jsoup 으로 정규화). */
    public byte[] convertHtmlToPdf(byte[] htmlBytes, String baseName) throws IOException {
        String raw = new String(htmlBytes, java.nio.charset.StandardCharsets.UTF_8);
        // jsoup 으로 XHTML 정규화 (openhtmltopdf 는 well-formed XML 만 받음)
        org.jsoup.nodes.Document jsoup = org.jsoup.Jsoup.parse(raw);
        jsoup.outputSettings()
                .syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml)
                .charset("UTF-8");
        String xhtml = jsoup.html();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        com.openhtmltopdf.pdfboxout.PdfRendererBuilder b = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
        b.useFastMode();
        // SSRF 심층방어: 임베드된 data: 리소스만 허용. http/https/file 절대 URL 서버측 fetch 차단.
        // (템플릿들은 인라인 <style> + data:image PNG 만 사용, 원격 CSS/폰트/이미지 미사용. 폰트는 useFont 로 등록.)
        b.useExternalResourceAccessControl(
                (uri, type) -> uri != null && uri.startsWith("data:"),
                com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        b.withHtmlContent(xhtml, null);
        b.toStream(out);
        // 한글 폰트 등록 — .ttc 는 openhtmltopdf 호환 안 됨. NanumGothic.ttf 우선.
        // openhtmltopdf 는 자동 글리프 폴백이 없어 HTML 의 font-family 와 등록 family 가 정확히 일치해야 함.
        // 템플릿이 쓰는 이름들 + sans-serif 폴백까지 동일 폰트로 등록해 한글 깨짐(###) 방지.
        java.io.File koFont = new java.io.File("/usr/share/fonts/truetype/nanum/NanumGothic.ttf");
        if (!koFont.exists()) koFont = new java.io.File("/usr/share/fonts/truetype/nanum/NanumBarunGothic.ttf");
        java.io.File koBold = new java.io.File("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf");
        for (String fam : new String[]{"Noto Sans KR", "NanumGothic", "Korean", "sans-serif"}) {
            if (koFont.exists()) {
                final java.io.File ff = koFont;
                b.useFont(() -> {
                    try { return new java.io.FileInputStream(ff); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }, fam);
            }
            if (koBold.exists()) {
                final java.io.File fb = koBold;
                b.useFont(() -> {
                    try { return new java.io.FileInputStream(fb); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }, fam, 700, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
            }
        }
        try {
            b.run();
        } catch (Exception e) {
            throw new IOException("PDF 변환 실패: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    /** 작업계획서 DOCX → PDF → 이메일 발송. 응답 본문은 PDF 바이트. */
    public byte[] sendWorksheetPdf(MultipartFile docxFile, String from, String to,
                                   String subject, String bodyText, String baseName)
            throws Exception {
        if (docxFile == null || docxFile.isEmpty()) {
            throw new IllegalArgumentException("DOCX 파일이 필요합니다");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("받는 사람 이메일이 필요합니다");
        }
        // 네이버/지메일 SMTP 는 From 이 반드시 계정 본인이어야 함. 사용자 입력 from 은 Reply-To 로.
        if (defaultFrom == null || defaultFrom.isBlank()) {
            throw new IllegalArgumentException("SMTP 발신 계정이 설정되지 않았습니다 (MAIL_USERNAME 미설정)");
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalArgumentException("JavaMailSender 가 설정되지 않았습니다 (MAIL_HOST/PORT 미설정)");
        }
        String replyTo = (from == null || from.isBlank()) ? null : from.trim();
        String safeBase = (baseName == null || baseName.isBlank())
                ? "작업계획서_" + UUID.randomUUID().toString().substring(0, 6)
                : baseName;

        byte[] pdf = convertDocxToPdf(docxFile.getBytes(), safeBase);

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(defaultFrom);
        if (replyTo != null && !replyTo.equalsIgnoreCase(defaultFrom)) {
            helper.setReplyTo(replyTo);
        }
        for (String t : to.split("[,;\\s]+")) {
            if (!t.isBlank()) helper.addTo(t.trim());
        }
        helper.setSubject(subject == null || subject.isBlank() ? "[SKEP] " + safeBase : subject);
        helper.setText(bodyText == null ? "" : bodyText, false);
        helper.addAttachment(safeBase + ".pdf", new ByteArrayResource(pdf));

        mailSender.send(msg);
        log.info("Worksheet PDF mail sent: from={} replyTo={} to={} pdfSize={}",
                defaultFrom, replyTo, to, pdf.length);
        return pdf;
    }
}
