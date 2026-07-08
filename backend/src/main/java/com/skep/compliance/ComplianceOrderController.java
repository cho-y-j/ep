package com.skep.compliance;

import com.skep.common.ApiException;
import com.skep.compliance.dto.*;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.storage.FileStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/compliance-orders")
@RequiredArgsConstructor
public class ComplianceOrderController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/webp",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final ComplianceOrderService service;
    private final FileStorage storage;

    @PostMapping
    public ComplianceOrderResponse issue(@Valid @RequestBody CreateComplianceOrderRequest req,
                                         @CurrentUser AuthenticatedUser actor) {
        return service.issue(req, actor);
    }

    /** scope=bp 면 BP 본인 발행 list, scope=supplier 면 공급사 본인 수신 list. */
    @GetMapping
    public List<ComplianceOrderResponse> list(@RequestParam(defaultValue = "supplier") String scope,
                                              @CurrentUser AuthenticatedUser actor) {
        return "bp".equalsIgnoreCase(scope)
                ? service.listForBp(actor)
                : service.listForSupplier(actor);
    }

    @GetMapping("/{id}")
    public ComplianceOrderResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplianceOrderResponse uploadProof(@PathVariable Long id,
                                                @RequestParam("file") MultipartFile file,
                                                @CurrentUser AuthenticatedUser actor) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "파일을 선택하세요");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw ApiException.badRequest("BAD_CONTENT_TYPE",
                    "허용되지 않은 파일 형식입니다: " + contentType + " (PDF/이미지/Office 만)");
        }
        String key = storage.store(file);
        service.attachProof(id, key, file.getOriginalFilename(), contentType, actor);
        return service.get(id, actor);
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<Resource> downloadProof(@PathVariable Long id,
                                                   @CurrentUser AuthenticatedUser actor) {
        ComplianceOrderService.ProofInfo info = service.getProofInfo(id, actor);
        Resource res = storage.load(info.storageKey());
        String filename = info.filename() != null ? info.filename() : ("proof-" + id);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.contentType() != null ? info.contentType() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .body(res);
    }

    @PostMapping("/{id}/submit")
    public ComplianceOrderResponse submit(@PathVariable Long id,
                                          @RequestBody SubmitComplianceRequest req,
                                          @CurrentUser AuthenticatedUser actor) {
        return service.submit(id, req, actor);
    }

    @PostMapping("/{id}/review")
    public ComplianceOrderResponse review(@PathVariable Long id,
                                          @Valid @RequestBody ReviewComplianceRequest req,
                                          @CurrentUser AuthenticatedUser actor) {
        return service.review(id, req, actor);
    }
}
