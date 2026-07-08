package com.skep.assignment;

import com.skep.assignment.dto.AssignRequest;
import com.skep.assignment.dto.AssignmentResponse;
import com.skep.assignment.dto.EquipmentCandidateResponse;
import com.skep.assignment.dto.PersonCandidateResponse;
import com.skep.assignment.dto.ReleaseRequest;
import com.skep.equipment.Equipment;
import com.skep.equipment.dto.EquipmentResponse;
import com.skep.person.Person;
import com.skep.person.dto.PersonResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 자원 ↔ 현장 배치/해제/이력/후보 조회 API.
 *
 * 기존 controller(equipment/person/site)는 그대로 두고, 배치 도메인은 별도 controller로 묶는다.
 */
@RestController
public class AssignmentController {

    private final AssignmentService service;

    public AssignmentController(AssignmentService service) {
        this.service = service;
    }

    // ----- Equipment -----

    @PostMapping("/api/equipment/{id}/assignment")
    public EquipmentResponse assignEquipment(@PathVariable Long id,
                                             @Valid @RequestBody AssignRequest req,
                                             @CurrentUser AuthenticatedUser actor) {
        Equipment e = service.assignEquipment(id, req, actor);
        return EquipmentResponse.from(e, 0L, service.resolveSiteName(e.getCurrentSiteId()));
    }

    @DeleteMapping("/api/equipment/{id}/assignment")
    public EquipmentResponse releaseEquipment(@PathVariable Long id,
                                              @RequestBody(required = false) ReleaseRequest req,
                                              @CurrentUser AuthenticatedUser actor) {
        Equipment e = service.releaseEquipment(id, req, actor);
        return EquipmentResponse.from(e);
    }

    @GetMapping("/api/equipment/{id}/assignments")
    public List<AssignmentResponse> equipmentHistory(@PathVariable Long id,
                                                     @CurrentUser AuthenticatedUser actor) {
        return service.equipmentHistory(id, actor);
    }

    // ----- Person -----

    @PostMapping("/api/persons/{id}/assignment")
    public PersonResponse assignPerson(@PathVariable Long id,
                                       @Valid @RequestBody AssignRequest req,
                                       @CurrentUser AuthenticatedUser actor) {
        Person p = service.assignPerson(id, req, actor);
        return PersonResponse.from(p, 0L, 0L, service.resolveSiteName(p.getCurrentSiteId()));
    }

    @DeleteMapping("/api/persons/{id}/assignment")
    public PersonResponse releasePerson(@PathVariable Long id,
                                        @RequestBody(required = false) ReleaseRequest req,
                                        @CurrentUser AuthenticatedUser actor) {
        Person p = service.releasePerson(id, req, actor);
        return PersonResponse.from(p);
    }

    @GetMapping("/api/persons/{id}/assignments")
    public List<AssignmentResponse> personHistory(@PathVariable Long id,
                                                  @CurrentUser AuthenticatedUser actor) {
        return service.personHistory(id, actor);
    }

    // ----- Site → 자원 -----

    @GetMapping("/api/sites/{id}/equipment")
    public List<EquipmentResponse> equipmentOnSite(@PathVariable Long id,
                                                   @CurrentUser AuthenticatedUser actor) {
        var list = service.equipmentOnSite(id, actor);
        // 모두 같은 site에 배치돼 있으므로 site name 1번만 조회
        String siteName = list.isEmpty() ? null : service.resolveSiteName(id);
        return list.stream().map(e -> EquipmentResponse.from(e, 0L, siteName)).toList();
    }

    @GetMapping("/api/sites/{id}/persons")
    public List<PersonResponse> personsOnSite(@PathVariable Long id,
                                              @CurrentUser AuthenticatedUser actor) {
        var list = service.personsOnSite(id, actor);
        String siteName = list.isEmpty() ? null : service.resolveSiteName(id);
        return list.stream().map(p -> PersonResponse.from(p, 0L, 0L, siteName)).toList();
    }

    @GetMapping("/api/sites/{id}/equipment-assignments")
    public List<AssignmentResponse> siteEquipmentHistory(@PathVariable Long id,
                                                         @CurrentUser AuthenticatedUser actor) {
        return service.siteEquipmentHistory(id, actor);
    }

    @GetMapping("/api/sites/{id}/person-assignments")
    public List<AssignmentResponse> sitePersonHistory(@PathVariable Long id,
                                                      @CurrentUser AuthenticatedUser actor) {
        return service.sitePersonHistory(id, actor);
    }

    // ----- Site → 후보 -----

    @GetMapping("/api/sites/{id}/equipment-candidates")
    public List<EquipmentCandidateResponse> equipmentCandidates(@PathVariable Long id,
                                                                @CurrentUser AuthenticatedUser actor) {
        return service.equipmentCandidates(id, actor);
    }

    @GetMapping("/api/sites/{id}/person-candidates")
    public List<PersonCandidateResponse> personCandidates(@PathVariable Long id,
                                                          @CurrentUser AuthenticatedUser actor) {
        return service.personCandidates(id, actor);
    }
}
