package com.skep.fieldDeployment;

import com.skep.fieldDeployment.dto.CreateFieldDeploymentRequest;
import com.skep.fieldDeployment.dto.FieldDeploymentBoardItem;
import com.skep.fieldDeployment.dto.FieldDeploymentResponse;
import com.skep.fieldDeployment.dto.ReviewFieldDeploymentRequest;
import com.skep.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/field-deployments")
@RequiredArgsConstructor
public class FieldDeploymentController {

    private final FieldDeploymentService service;

    @PostMapping
    public FieldDeploymentResponse create(@Valid @RequestBody CreateFieldDeploymentRequest req,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @GetMapping("/supplier")
    public List<FieldDeploymentResponse> listSupplier(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.listForSupplier(actor);
    }

    @GetMapping("/bp")
    public List<FieldDeploymentResponse> listBp(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.listForBp(actor);
    }

    @GetMapping("/bp/board")
    public List<FieldDeploymentBoardItem> bpActiveBoard(@AuthenticationPrincipal AuthenticatedUser actor) {
        return service.activeBoard(actor);
    }

    @PostMapping("/{id}/accept")
    public FieldDeploymentResponse accept(@PathVariable Long id,
                                          @RequestBody(required = false) AcceptFieldDeploymentRequest req,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.accept(id, req != null ? req.note() : null, req != null ? req.targetSiteId() : null, actor);
    }

    public record AcceptFieldDeploymentRequest(String note, Long targetSiteId) {}

    @PostMapping("/{id}/reject")
    public FieldDeploymentResponse reject(@PathVariable Long id,
                                          @RequestBody(required = false) ReviewFieldDeploymentRequest req,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.reject(id, req, actor);
    }

    @PostMapping("/{id}/cancel")
    public FieldDeploymentResponse cancel(@PathVariable Long id,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.cancel(id, actor);
    }

    @PostMapping("/{id}/complete")
    public FieldDeploymentResponse complete(@PathVariable Long id,
                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.complete(id, actor);
    }
}
