package com.skep.worksheet;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * skep v1 의 WorksheetEditorController 를 v2 로 이식.
 * OnlyOffice 가 callback / editor-file 을 인증 없이 호출해야 하므로 SecurityConfig 에서 permitAll 처리.
 */
@RestController
@RequestMapping("/api/worksheet")
public class WorksheetEditorController {

    private final WorksheetEditorService editorService;
    private final WorksheetMailService mailService;

    public WorksheetEditorController(WorksheetEditorService editorService, WorksheetMailService mailService) {
        this.editorService = editorService;
        this.mailService = mailService;
    }

    private static ResponseEntity<byte[]> attachment(byte[] bytes, String name, String ext, MediaType type) {
        String dn = URLEncoder.encode((name != null ? name : "worksheet") + "." + ext,
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + dn)
                .body(bytes);
    }

    /** 로그인 사용자만 세션 생성. ADMIN/BP 권한 검사는 client side 진입(create 페이지)에서 이미 됨. */
    @PostMapping(value = "/editor-session", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BP')")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String baseName,
            @RequestParam(value = "userName", required = false) String userName
    ) throws Exception {
        return ResponseEntity.ok(editorService.createSession(file, userName, baseName));
    }

    /** OnlyOffice 컨테이너가 호출 — 인증 없이 sessionId 만으로 동작. SecurityConfig 에서 permitAll.
     *  .docx 확장자 alias 도 매핑 — OnlyOffice 가 URL 확장자로 파일 타입 추정하므로 권장 경로. */
    @org.springframework.web.bind.annotation.RequestMapping(
            value = {"/editor-file/{sessionId}", "/editor-file/{sessionId}.docx"},
            method = { org.springframework.web.bind.annotation.RequestMethod.GET,
                       org.springframework.web.bind.annotation.RequestMethod.HEAD })
    public ResponseEntity<byte[]> getFile(@PathVariable String sessionId) throws Exception {
        byte[] bytes = editorService.readSession(sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sessionId + ".docx\"")
                .contentLength(bytes.length)
                .body(bytes);
    }

    /** OnlyOffice 콜백 — 인증 없이. SecurityConfig 에서 permitAll. */
    @PostMapping("/onlyoffice-callback/{sessionId}")
    public ResponseEntity<Map<String, Object>> callback(@PathVariable String sessionId,
                                                         @RequestBody JsonNode body) {
        return ResponseEntity.ok(editorService.handleCallback(sessionId, body));
    }

    @GetMapping("/editor-session/{sessionId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'BP')")
    public ResponseEntity<byte[]> download(@PathVariable String sessionId,
                                           @RequestParam(value = "name", required = false) String name) throws Exception {
        return attachment(editorService.readSession(sessionId), name, "docx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @GetMapping("/editor-session/{sessionId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'BP')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String sessionId,
                                               @RequestParam(value = "name", required = false) String name) throws Exception {
        byte[] docx = editorService.readSession(sessionId);
        byte[] pdf = mailService.convertDocxToPdf(docx, name != null ? name : "worksheet");
        return attachment(pdf, name, "pdf", MediaType.APPLICATION_PDF);
    }
}
