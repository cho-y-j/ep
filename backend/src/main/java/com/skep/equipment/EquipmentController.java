package com.skep.equipment;

import com.skep.equipment.dto.CreateEquipmentRequest;
import com.skep.equipment.dto.EquipmentResponse;
import com.skep.equipment.dto.UpdateEquipmentRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService service;

    public EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<EquipmentResponse> list(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) EquipmentCategory category
    ) {
        return service.list(actor, supplierId, category).stream().map(EquipmentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public EquipmentResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return EquipmentResponse.from(service.get(id, actor));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponse create(@Valid @RequestBody CreateEquipmentRequest req, @CurrentUser AuthenticatedUser actor) {
        return EquipmentResponse.from(service.create(req, actor));
    }

    @PatchMapping("/{id}")
    public EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEquipmentRequest req, @CurrentUser AuthenticatedUser actor) {
        return EquipmentResponse.from(service.update(id, req, actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
