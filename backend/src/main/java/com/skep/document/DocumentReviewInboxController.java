package com.skep.document;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** BP사 계정 "받은 서류 심사" 수신함. */
@RestController
@RequestMapping("/api/document-reviews")
@RequiredArgsConstructor
public class DocumentReviewInboxController {

    private final DocumentReviewInboxService service;

    /** BP/ADMIN 이 받은 서류 심사 목록. */
    @GetMapping("/received")
    public List<Map<String, Object>> received(@CurrentUser AuthenticatedUser actor) {
        return service.listReceived(actor);
    }

    /** 읽음 처리. */
    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.markRead(id, actor);
    }

    /** 봉투 상세 — 자원별 문서 목록(인라인 뷰어/승인 판단용). */
    @GetMapping("/{id}/documents")
    public List<Map<String, Object>> documents(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.listDocuments(id, actor);
    }

    /** 승인 — 수신 BP 만, 심사중에서만. */
    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.approve(id, actor);
    }

    public record RejectRequest(String reason) {}

    /** 반려 — 수신 BP 만, 심사중에서만. 사유 필수. */
    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody RejectRequest req,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.reject(id, req == null ? null : req.reason(), actor);
    }

    /** 자원별 폴더로 묶은 서류 zip 다운로드. */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        byte[] bytes = service.download(id, actor);
        String fname = "document-review-" + id + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .body(bytes);
    }

    public record BulkDownloadRequest(List<Long> ids) {}

    /** 선택한 여러 봉투를 한 번에 묶은 zip 다운로드. */
    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadBulk(@RequestBody BulkDownloadRequest req,
                                               @CurrentUser AuthenticatedUser actor) {
        byte[] bytes = service.downloadBulk(req.ids(), actor);
        String fname = "document-reviews-" + (req.ids() == null ? 0 : req.ids().size()) + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                .body(bytes);
    }
}
