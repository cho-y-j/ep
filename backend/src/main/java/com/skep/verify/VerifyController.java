package com.skep.verify;

import com.skep.document.dto.DocumentResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class VerifyController {

    private final VerificationService service;

    public VerifyController(VerificationService service) {
        this.service = service;
    }

    /**
     * 서류 자동 검증 트리거.
     * Body: { "user_inputs": { "license_no": "...", "name": "...", ... } } (선택)
     *
     * 흐름은 VerificationService.verifyDocument 참고.
     */
    @PostMapping("/{id}/verify")
    public DocumentResponse verify(@PathVariable Long id,
                                   @RequestBody(required = false) VerifyRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        Map<String, String> inputs = (req != null && req.userInputs() != null) ? req.userInputs() : Map.of();
        return service.verifyDocument(id, inputs, actor);
    }

    /**
     * 서류 반려. ADMIN 만 가능. 사유 필수.
     */
    @PostMapping("/{id}/reject")
    public DocumentResponse reject(@PathVariable Long id,
                                   @RequestBody RejectRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        String reason = req != null ? req.reason() : null;
        return service.rejectDocument(id, reason, actor);
    }

    public record VerifyRequest(Map<String, String> userInputs) {}
    public record RejectRequest(String reason) {}
}
