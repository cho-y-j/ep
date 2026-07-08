package com.skep.supplement;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.supplement.dto.CreateSupplementRequest;
import com.skep.supplement.dto.SupplementResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** S-11: 서류 보완 요청 API. */
@RestController
@RequestMapping("/api/document-supplements")
public class DocumentSupplementController {

    private final DocumentSupplementService service;

    public DocumentSupplementController(DocumentSupplementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplementResponse create(@Valid @RequestBody CreateSupplementRequest req,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    /** 다건 보완요청 — 공급사별 알림 1건으로 집계. */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SupplementResponse> createBatch(@Valid @RequestBody List<CreateSupplementRequest> reqs,
                                                @CurrentUser AuthenticatedUser actor) {
        return service.createBatch(reqs, actor);
    }

    @GetMapping
    public List<SupplementResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    @GetMapping("/{id}")
    public SupplementResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    @PostMapping("/{id}/cancel")
    public SupplementResponse cancel(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.cancel(id, actor);
    }
}
