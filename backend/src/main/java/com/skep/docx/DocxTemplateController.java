package com.skep.docx;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/docx-templates")
public class DocxTemplateController {

    private final DocxTemplateService service;

    public DocxTemplateController(DocxTemplateService service) { this.service = service; }

    @GetMapping
    public List<TemplateResponse> list(@RequestParam(defaultValue = DocxTemplateService.TARGET_WORK_PLAN) String targetType,
                                       @CurrentUser AuthenticatedUser actor) {
        return service.listVisible(targetType, actor).stream().map(TemplateResponse::from).toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    public TemplateResponse upload(@RequestParam(defaultValue = DocxTemplateService.TARGET_WORK_PLAN) String targetType,
                                   @RequestParam(required = false) Long companyId,
                                   @RequestParam @NotBlank @Size(max = 120) String name,
                                   @RequestPart("file") MultipartFile file,
                                   @CurrentUser AuthenticatedUser actor) {
        return TemplateResponse.from(service.upload(targetType, companyId, name, file, actor));
    }

    @PatchMapping("/{id}")
    public TemplateResponse rename(@PathVariable Long id,
                                   @RequestBody RenameRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        return TemplateResponse.from(service.rename(id, req.name(), actor));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
    }

    /** 원본 템플릿 파일 다운로드 (관리/검사용). */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        DocxTemplate t = service.getForExport(id, actor);
        Resource res = service.loadFile(t);
        String fname = URLEncoder.encode(t.getName() + ".docx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fname)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(res);
    }

    public record RenameRequest(@NotBlank @Size(max = 120) String name) {}

    public record TemplateResponse(
            Long id, String targetType, Long companyId, String name,
            Long fileSize, LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static TemplateResponse from(DocxTemplate t) {
            return new TemplateResponse(t.getId(), t.getTargetType(), t.getCompanyId(),
                    t.getName(), t.getFileSize(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
