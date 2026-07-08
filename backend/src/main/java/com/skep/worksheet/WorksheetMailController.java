package com.skep.worksheet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * S-9-C: skep 원본 /api/worksheet/{to-pdf,send-pdf} 호환 컨트롤러.
 */
@RestController
@RequestMapping("/api/worksheet")
public class WorksheetMailController {

    private static final Logger log = LoggerFactory.getLogger(WorksheetMailController.class);

    private final WorksheetMailService mailService;

    public WorksheetMailController(WorksheetMailService mailService) {
        this.mailService = mailService;
    }

    /** DOCX → PDF (다운로드용). ADMIN/BP 만 — LibreOffice DoS 방어. */
    @PostMapping(value = "/to-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public ResponseEntity<byte[]> toPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String baseName
    ) throws Exception {
        byte[] pdf = mailService.convertDocxToPdf(file.getBytes(),
                baseName != null ? baseName : "worksheet");
        String downloadName = URLEncoder.encode(
                (baseName != null ? baseName : "worksheet") + ".pdf",
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + downloadName)
                .body(pdf);
    }

    /** DOCX + 메일 정보 → PDF 변환 후 발송. ADMIN/BP 만 — SMTP relay 방지. */
    @PostMapping(value = "/send-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    public ResponseEntity<Map<String, Object>> sendPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam("to") String to,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "body", required = false) String body,
            @RequestParam(value = "name", required = false) String baseName
    ) {
        try {
            byte[] pdf = mailService.sendWorksheetPdf(file, from, to, subject, body, baseName);
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "to", to,
                    "pdfSize", pdf.length,
                    "message", "메일 발송 완료"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("메일 발송 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "메일 발송 실패: " + e.getMessage()
            ));
        }
    }
}
