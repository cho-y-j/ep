package com.skep.clientorg.history;

import com.skep.equipment.EquipmentService;
import com.skep.person.PersonService;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client-org-history")
public class ResourceHistoryController {

    private final ResourceHistoryService service;
    private final EquipmentService equipmentService;
    private final PersonService personService;

    public ResourceHistoryController(ResourceHistoryService service,
                                     EquipmentService equipmentService,
                                     PersonService personService) {
        this.service = service;
        this.equipmentService = equipmentService;
        this.personService = personService;
    }

    // 조회 — 그 자원에 view 권한이 있어야 history 도 노출 (IDOR 차단)

    @GetMapping("/equipment/{id}")
    public List<HistoryDto> equipmentHistory(@PathVariable Long id,
                                              @CurrentUser AuthenticatedUser actor) {
        equipmentService.get(id, actor); // 권한 가드 — 통과 못 하면 throw
        return service.listEquipmentHistory(id);
    }

    @GetMapping("/person/{id}")
    public List<HistoryDto> personHistory(@PathVariable Long id,
                                          @CurrentUser AuthenticatedUser actor) {
        personService.get(id, actor); // 권한 가드 — 통과 못 하면 throw
        return service.listPersonHistory(id);
    }

    // ── ADMIN 수동 ───────────────────────────────────────

    @PostMapping("/equipment/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public HistoryDto addEquipment(@PathVariable Long id,
                                    @Valid @RequestBody HistoryUpsertRequest req,
                                    @CurrentUser AuthenticatedUser actor) {
        return service.addEquipmentHistory(id, req, actor);
    }

    @PatchMapping("/equipment-history/{historyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public HistoryDto updateEquipment(@PathVariable Long historyId,
                                       @Valid @RequestBody HistoryUpsertRequest req) {
        return service.updateEquipmentHistory(historyId, req);
    }

    @DeleteMapping("/equipment-history/{historyId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEquipment(@PathVariable Long historyId) {
        service.deleteEquipmentHistory(historyId);
    }

    @PostMapping("/person/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public HistoryDto addPerson(@PathVariable Long id,
                                 @Valid @RequestBody HistoryUpsertRequest req,
                                 @CurrentUser AuthenticatedUser actor) {
        return service.addPersonHistory(id, req, actor);
    }

    @PatchMapping("/person-history/{historyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public HistoryDto updatePerson(@PathVariable Long historyId,
                                    @Valid @RequestBody HistoryUpsertRequest req) {
        return service.updatePersonHistory(historyId, req);
    }

    @DeleteMapping("/person-history/{historyId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePerson(@PathVariable Long historyId) {
        service.deletePersonHistory(historyId);
    }
}
