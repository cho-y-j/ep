package com.skep.resourceCheck;

import com.skep.resourceCheck.dto.IssueRequest;
import com.skep.resourceCheck.dto.ResourceCheckResponse;
import com.skep.resourceCheck.dto.ReviewRequest;
import com.skep.resourceCheck.dto.SubmitRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resource-checks")
@RequiredArgsConstructor
public class ResourceCheckController {

    private final ResourceCheckService service;

    /** 점검 요청 발행 — BP/공급사(자기+자식 자원)/ADMIN. */
    @PostMapping
    public ResourceCheckResponse issue(@Valid @RequestBody IssueRequest req,
                                        @CurrentUser AuthenticatedUser actor) {
        return service.issue(req, actor);
    }

    /** 공급사 회신 — documentId 만 첨부. */
    @PostMapping("/{id}/submit")
    public ResourceCheckResponse submit(@PathVariable Long id,
                                         @Valid @RequestBody SubmitRequest req,
                                         @CurrentUser AuthenticatedUser actor) {
        return service.submit(id, req, actor);
    }

    /** 공급사 회신 — 파일 직접 업로드. 자원 서류로 저장 후 점검에 연결.
     *  파일 1개면 그대로, 2개 이상이면 올린 순서대로 1개 PDF로 병합 후 저장. */
    @PostMapping(value = "/{id}/submit-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResourceCheckResponse submitWithFile(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile[] files,
                                                  @CurrentUser AuthenticatedUser actor) {
        return service.submitWithFile(id, files, actor);
    }

    /** 발행사(BP/공급사)/ADMIN 승인. */
    @PostMapping("/{id}/approve")
    public ResourceCheckResponse approve(@PathVariable Long id,
                                          @RequestBody(required = false) ReviewRequest req,
                                          @CurrentUser AuthenticatedUser actor) {
        return service.approve(id, req, actor);
    }

    /** 발행사(BP/공급사)/ADMIN 반려. */
    @PostMapping("/{id}/reject")
    public ResourceCheckResponse reject(@PathVariable Long id,
                                         @RequestBody(required = false) ReviewRequest req,
                                         @CurrentUser AuthenticatedUser actor) {
        return service.reject(id, req, actor);
    }

    /** 발행 목록 (BP/공급사 자기 발행분, ADMIN 전체). */
    @GetMapping("/bp-list")
    public List<ResourceCheckResponse> listForBp(@CurrentUser AuthenticatedUser actor) {
        return service.listForBp(actor);
    }

    /** 공급사 수신함. */
    @GetMapping("/supplier-list")
    public List<ResourceCheckResponse> listForSupplier(@CurrentUser AuthenticatedUser actor) {
        return service.listForSupplier(actor);
    }

    /** 작업계획서별. */
    @GetMapping("/work-plan/{workPlanId}")
    public List<ResourceCheckResponse> listForWorkPlan(@PathVariable Long workPlanId,
                                                       @CurrentUser AuthenticatedUser actor) {
        return service.listForWorkPlan(workPlanId, actor);
    }
}
