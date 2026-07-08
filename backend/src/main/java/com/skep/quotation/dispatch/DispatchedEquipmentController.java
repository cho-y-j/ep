package com.skep.quotation.dispatch;

import com.skep.quotation.dispatch.dto.DispatchRequest;
import com.skep.quotation.dispatch.dto.DispatchedEquipmentResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotations/{requestId}/dispatched")
@RequiredArgsConstructor
public class DispatchedEquipmentController {

    private final DispatchedEquipmentService service;

    /** 공급사가 선정된 후 차량 다중 선택 + 단가로 send. 1회 멱등 (409). */
    @PostMapping
    public List<DispatchedEquipmentResponse> send(
            @PathVariable Long requestId,
            @Valid @RequestBody DispatchRequest req,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.send(requestId, req, actor);
    }

    /** BP / ADMIN / 해당 공급사가 견적별 dispatched 차량 list 조회. */
    @GetMapping
    public List<DispatchedEquipmentResponse> list(
            @PathVariable Long requestId,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.listByRequest(requestId, actor);
    }
}
