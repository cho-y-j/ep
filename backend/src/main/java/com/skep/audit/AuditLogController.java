package com.skep.audit;

import com.skep.audit.dto.AuditLogResponse;
import com.skep.common.PageResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pg = service.list(actor, page, size);
        return PageResponse.of(pg, r -> AuditLogResponse.from(r.log()));
    }

    @GetMapping("/recent")
    public List<AuditLogResponse> recent(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return service.recent(actor, limit).stream()
                .map(r -> AuditLogResponse.from(r.log()))
                .toList();
    }
}
