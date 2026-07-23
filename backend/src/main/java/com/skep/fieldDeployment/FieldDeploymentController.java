package com.skep.fieldDeployment;

import com.skep.fieldDeployment.dto.CreateComboFieldDeploymentRequest;
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

    /** R3: 조합(장비+교대조 조종원) 투입 요청 — 장비 1행 + 조종원 N행 단일 트랜잭션 생성. */
    @PostMapping("/combo")
    public List<FieldDeploymentResponse> createCombo(@Valid @RequestBody CreateComboFieldDeploymentRequest req,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.createCombo(req, actor);
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

    /** R3: 조합 일괄 수락 — 전건 REQUESTED·같은 combo·자기 수신분, 위반 시 전체 롤백. */
    @PostMapping("/accept-combo")
    public List<FieldDeploymentResponse> acceptCombo(@RequestBody AcceptComboFieldDeploymentRequest req,
                                                     @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.acceptCombo(req.requestIds(), req.note(), req.targetSiteId(), actor);
    }

    public record AcceptComboFieldDeploymentRequest(List<Long> requestIds, String note, Long targetSiteId) {}

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
