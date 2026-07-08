package com.skep.quotation.bundle;

import com.skep.quotation.bundle.dto.BundleResponse;
import com.skep.quotation.bundle.dto.SendBundleRequest;
import com.skep.quotation.dispatch.DispatchedEquipmentService;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quotations/{requestId}/document-bundle")
@RequiredArgsConstructor
public class DocumentBundleController {

    private final DocumentBundleService service;
    private final DispatchedEquipmentService dispatchedService;

    /** 공급사가 서류 묶음 send (1회 멱등, includeEmail 옵션). */
    @PostMapping
    public BundleResponse send(
            @PathVariable Long requestId,
            @RequestBody(required = false) SendBundleRequest req,
            @CurrentUser AuthenticatedUser actor
    ) {
        return service.send(requestId, req, actor);
    }

    /** 견적별 받은 묶음 list — ADMIN / 해당 BP / 선정된 공급사 본인만. */
    @GetMapping
    public List<BundleResponse> list(@PathVariable Long requestId,
                                     @CurrentUser AuthenticatedUser actor) {
        dispatchedService.ensureCanReadRequest(requestId, actor);
        return service.listByRequest(requestId, actor);
    }
}
