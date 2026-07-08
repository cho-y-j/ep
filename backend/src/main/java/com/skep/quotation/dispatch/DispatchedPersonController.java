package com.skep.quotation.dispatch;

import com.skep.quotation.dispatch.dto.DispatchPersonRequest;
import com.skep.quotation.dispatch.dto.DispatchedPersonResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotations/{requestId}/dispatched-persons")
@RequiredArgsConstructor
public class DispatchedPersonController {

    private final DispatchedPersonService service;

    /** 공급사가 선정된 후 인원 다중 선택 + 단가로 send. 1회 멱등 (409). */
    @PostMapping
    public List<DispatchedPersonResponse> send(
            @PathVariable Long requestId,
            @Valid @RequestBody DispatchPersonRequest req,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.send(requestId, req, actor);
    }

    /** BP / ADMIN / 해당 공급사가 견적별 dispatched 인원 list 조회. */
    @GetMapping
    public List<DispatchedPersonResponse> list(
            @PathVariable Long requestId,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.listByRequest(requestId, actor);
    }
}
