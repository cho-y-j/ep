package com.skep.workplan;

import com.skep.common.PageResponse;
import com.skep.equipment.dto.EquipmentResponse;
import com.skep.person.dto.PersonResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.workplan.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-plans")
public class WorkPlanController {

    private final WorkPlanService service;

    public WorkPlanController(WorkPlanService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<WorkPlanResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser AuthenticatedUser actor) {
        return PageResponse.of(service.list(actor, page, size), wp -> wp);
    }

    @GetMapping("/{id}")
    public WorkPlanResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    /** 제출 전 누락 자원+서류 미리보기 — 보완요청 일괄 발송 모달에 사용. */
    @GetMapping("/{id}/missing-docs")
    public java.util.List<java.util.Map<String, Object>> missingDocs(@PathVariable Long id,
                                                                     @CurrentUser AuthenticatedUser actor) {
        return service.listMissingDocs(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPlanResponse create(@Valid @RequestBody CreateWorkPlanRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        WorkPlan wp = service.create(req, actor);
        return service.get(wp.getId(), actor);
    }

    @PatchMapping("/{id}")
    public WorkPlanResponse update(@PathVariable Long id, @Valid @RequestBody UpdateWorkPlanRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        service.update(id, req, actor);
        return service.get(id, actor);
    }

    /** S-9-B: 워크시트 폼 상태 (132 필드 + role_assign + 첨부 선택). */
    @PatchMapping("/{id}/form-values")
    public WorkPlanResponse updateFormValues(@PathVariable Long id,
                                             @RequestBody UpdateFormValuesRequest req,
                                             @CurrentUser AuthenticatedUser actor) {
        service.updateFormValues(id, req, actor);
        return service.get(id, actor);
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPlanResponse clone(@PathVariable Long id, @Valid @RequestBody(required = false) CloneWorkPlanRequest req,
                                  @CurrentUser AuthenticatedUser actor) {
        WorkPlan wp = service.clone(id, req != null ? req : new CloneWorkPlanRequest(null, null), actor);
        return service.get(wp.getId(), actor);
    }

    @PostMapping("/{id}/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPlanResponse addEquipment(@PathVariable Long id, @Valid @RequestBody AddEquipmentRequest req,
                                         @CurrentUser AuthenticatedUser actor) {
        return service.addEquipment(id, req, actor);
    }

    @DeleteMapping("/{id}/equipment/{equipmentId}")
    public WorkPlanResponse removeEquipment(@PathVariable Long id, @PathVariable Long equipmentId,
                                            @CurrentUser AuthenticatedUser actor) {
        return service.removeEquipment(id, equipmentId, actor);
    }

    @PostMapping("/{id}/persons")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkPlanResponse addPerson(@PathVariable Long id, @Valid @RequestBody AddPersonRequest req,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.addPerson(id, req, actor);
    }

    @DeleteMapping("/{id}/persons/{personId}")
    public WorkPlanResponse removePerson(@PathVariable Long id, @PathVariable Long personId,
                                         @CurrentUser AuthenticatedUser actor) {
        return service.removePerson(id, personId, actor);
    }

    @PostMapping("/{id}/submit")
    public WorkPlanResponse submit(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.submit(id, actor);
        return service.get(id, actor);
    }

    @PostMapping("/{id}/approve")
    public WorkPlanResponse approve(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.approve(id, actor);
        return service.get(id, actor);
    }

    @PostMapping("/{id}/start")
    public WorkPlanResponse start(@PathVariable Long id,
                                  @RequestBody(required = false) StartWorkPlanRequest req,
                                  @CurrentUser AuthenticatedUser actor) {
        service.start(id, req, actor);
        return service.get(id, actor);
    }

    @PostMapping("/{id}/complete")
    public WorkPlanResponse complete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.complete(id, actor);
        return service.get(id, actor);
    }

    /** 작업계획서에 추가 가능한 장비 후보 (사이트 ACTIVE EQUIPMENT_SUPPLIER 자원만). */
    @GetMapping("/{id}/candidates/equipment")
    public List<EquipmentResponse> equipmentCandidates(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.equipmentCandidates(id, actor);
    }

    /** 작업계획서에 추가 가능한 인원 후보 (사이트 ACTIVE MANPOWER_SUPPLIER 소속만). */
    @GetMapping("/{id}/candidates/persons")
    public List<PersonResponse> personCandidates(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.personCandidates(id, actor);
    }

    @PostMapping("/{id}/cancel")
    public WorkPlanResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        service.cancel(id, req, actor);
        return service.get(id, actor);
    }
}
