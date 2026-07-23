package com.skep.equipment.timeline;

import com.skep.equipment.EquipmentService;
import com.skep.equipment.timeline.dto.EquipmentTimelineResponse;
import com.skep.equipment.timeline.dto.EquipmentTimelineResponse.*;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/equipment/{id}/timeline")
public class EquipmentTimelineController {

    private final EquipmentService equipmentService;
    private final EquipmentInspectionRepository inspections;
    private final EquipmentOperationRepository operations;
    private final EquipmentLocationRepository locations;
    private final EquipmentMaintenanceRepository maintenances;
    private final EquipmentNoteRepository notes;

    public EquipmentTimelineController(EquipmentService equipmentService,
                                       EquipmentInspectionRepository inspections,
                                       EquipmentOperationRepository operations,
                                       EquipmentLocationRepository locations,
                                       EquipmentMaintenanceRepository maintenances,
                                       EquipmentNoteRepository notes) {
        this.equipmentService = equipmentService;
        this.inspections = inspections;
        this.operations = operations;
        this.locations = locations;
        this.maintenances = maintenances;
        this.notes = notes;
    }

    @GetMapping
    public EquipmentTimelineResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        // 권한 체크: 본인 회사 또는 ADMIN — 기존 EquipmentService.get() 통해 검증
        equipmentService.get(id, actor);

        return new EquipmentTimelineResponse(
                inspections.findByEquipmentIdOrderByInspectedAtDesc(id).stream().map(InspectionItem::from).toList(),
                operations.findByEquipmentIdOrderByStartedAtDesc(id).stream().map(OperationItem::from).toList(),
                locations.findByEquipmentIdOrderByRecordedAtDesc(id).stream().map(LocationItem::from).toList(),
                maintenances.findByEquipmentIdOrderByMaintainedAtDesc(id).stream().map(MaintenanceItem::from).toList(),
                notes.findByEquipmentIdOrderByCreatedAtDesc(id).stream().map(NoteItem::from).toList()
        );
    }

    /** 정비 이력 1건 추가 — 수정 권한(소유 공급사+직속 자식+ADMIN)만. 기존 equipment_maintenance_history 테이블 재사용. */
    @PostMapping("/maintenance")
    public MaintenanceItem addMaintenance(@PathVariable Long id,
                                          @Valid @RequestBody CreateMaintenanceRequest req,
                                          @CurrentUser AuthenticatedUser actor) {
        equipmentService.getForModify(id, actor);
        EquipmentMaintenance m = maintenances.save(EquipmentMaintenance.builder()
                .equipmentId(id)
                .maintainedAt(req.maintainedAt())
                .maintainer(req.maintainer())
                .title(req.title())
                .description(req.description())
                .cost(req.cost())
                .build());
        return MaintenanceItem.from(m);
    }

    public record CreateMaintenanceRequest(
            @NotNull LocalDate maintainedAt,
            @Size(max = 100) String maintainer,
            @NotBlank @Size(max = 255) String title,
            String description,
            Long cost
    ) {}
}
