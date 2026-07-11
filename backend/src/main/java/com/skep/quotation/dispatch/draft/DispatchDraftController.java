package com.skep.quotation.dispatch.draft;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotations")
@RequiredArgsConstructor
public class DispatchDraftController {

    private final DispatchDraftService service;

    /** 견적별 배차 초안(DRAFT) 조회. 공급사=본인분, BP/ADMIN=전체. */
    @GetMapping("/{requestId}/dispatch-drafts")
    public List<DispatchDraftResponse> list(
            @PathVariable Long requestId,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.listByRequest(requestId, actor);
    }

    /** 공급사가 초안 "확인 후 발송" — 기존 send() 로 실제 dispatched 생성. */
    @PostMapping("/dispatch-drafts/{draftId}/confirm")
    public DispatchDraftResponse confirm(
            @PathVariable Long draftId,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.confirm(draftId, actor);
    }
}
