package com.skep.onlyoffice;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OnlyOffice 통합 endpoint.
 *
 * - status, work-plan/{id}/config: 사용자 JWT 인증 필요
 * - work-plan/{id}/file, work-plan/{id}/callback: 별도 token (signFileAccessToken) 으로 OnlyOffice Document Server 가 직접 접근
 *
 * 후자 두 endpoint 는 SecurityConfig 에서 permitAll 로 열어준다 — 토큰 자체로 검증.
 */
@RestController
@RequestMapping("/api/onlyoffice")
public class OnlyOfficeController {

    private final OnlyOfficeService service;

    public OnlyOfficeController(OnlyOfficeService service) { this.service = service; }

    @GetMapping("/status")
    public Map<String, Object> status() { return service.status(); }

    @GetMapping("/work-plan/{id}/config")
    public Map<String, Object> config(@PathVariable Long id,
                                      @RequestParam(required = false) Long templateId,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.buildEditorConfig(id, templateId, actor);
    }

    @GetMapping({"/work-plan/{id}/file", "/work-plan/{id}/file.docx"})
    public ResponseEntity<Resource> file(@PathVariable Long id, @RequestParam String token) {
        Resource res = service.loadFile(id, token);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(res);
    }

    @PostMapping("/work-plan/{id}/callback")
    public Map<String, Object> callback(@PathVariable Long id,
                                        @RequestParam String token,
                                        @RequestBody Map<String, Object> body) {
        return service.handleCallback(id, token, body);
    }
}
