package com.skep.equipment.timeline;

import com.skep.equipment.EquipmentService;
import com.skep.equipment.timeline.dto.EquipmentTimelineResponse;
import com.skep.equipment.timeline.dto.EquipmentTimelineResponse.*;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
