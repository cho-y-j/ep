package com.skep.resourcechange;

import com.skep.resourcechange.dto.CreateResourceChangeRequest;
import com.skep.resourcechange.dto.ResourceChangeRequestResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 업체변경 신청서 v0 (L2a). 생성(공급사·BP·ADMIN) · 목록 · 상세. */
@RestController
@RequestMapping("/api/resource-change-requests")
@RequiredArgsConstructor
public class ResourceChangeRequestController {

    private final ResourceChangeRequestService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceChangeRequestResponse create(@Valid @RequestBody CreateResourceChangeRequest req,
                                                @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @GetMapping
    public List<ResourceChangeRequestResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    @GetMapping("/{id}")
    public ResourceChangeRequestResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }
}
