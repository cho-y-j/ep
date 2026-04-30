package com.skep.equipment;

import com.skep.equipment.dto.CreateEquipmentRequest;
import com.skep.equipment.dto.EquipmentResponse;
import com.skep.equipment.dto.UpdateEquipmentRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
        var list = service.list(actor, supplierId, category);
        var ids = list.stream().map(Equipment::getId).toList();
        var counts = service.expiringCountsByEquipmentIds(ids);
        return list.stream()
                .map(e -> EquipmentResponse.from(e, counts.getOrDefault(e.getId(), 0L)))
                .toList();
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

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EquipmentResponse uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser actor
    ) {
        return EquipmentResponse.from(service.uploadPhoto(id, file, actor));
    }

    @DeleteMapping("/{id}/photo")
    public EquipmentResponse deletePhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return EquipmentResponse.from(service.deletePhoto(id, actor));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        EquipmentService.PhotoData data = service.loadPhoto(id, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType() != null ? data.contentType() : "application/octet-stream"))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(data.resource());
    }
}
