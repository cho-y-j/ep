package com.skep.equipment.timeline.dto;

import com.skep.equipment.timeline.EquipmentInspection;
import com.skep.equipment.timeline.EquipmentLocation;
import com.skep.equipment.timeline.EquipmentMaintenance;
import com.skep.equipment.timeline.EquipmentNote;
import com.skep.equipment.timeline.EquipmentOperation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EquipmentTimelineResponse(
        List<InspectionItem> inspections,
        List<OperationItem> operations,
        List<LocationItem> locations,
        List<MaintenanceItem> maintenances,
        List<NoteItem> notes
) {
    public record InspectionItem(
            Long id, LocalDate inspectedAt, String inspector, String title,
            String result, String note, LocalDate nextInspectionAt
    ) {
        public static InspectionItem from(EquipmentInspection x) {
            return new InspectionItem(x.getId(), x.getInspectedAt(), x.getInspector(),
                    x.getTitle(), x.getResult(), x.getNote(), x.getNextInspectionAt());
        }
    }

    public record OperationItem(
            Long id, LocalDateTime startedAt, LocalDateTime endedAt, String siteName,
            String description, Integer utilizationPct, String status
    ) {
        public static OperationItem from(EquipmentOperation x) {
            return new OperationItem(x.getId(), x.getStartedAt(), x.getEndedAt(),
                    x.getSiteName(), x.getDescription(), x.getUtilizationPct(), x.getStatus());
        }
    }

    public record LocationItem(
            Long id, LocalDateTime recordedAt, String locationName, String note
    ) {
        public static LocationItem from(EquipmentLocation x) {
            return new LocationItem(x.getId(), x.getRecordedAt(), x.getLocationName(), x.getNote());
        }
    }

    public record MaintenanceItem(
            Long id, LocalDate maintainedAt, String maintainer, String title,
            String description, Long cost
    ) {
        public static MaintenanceItem from(EquipmentMaintenance x) {
            return new MaintenanceItem(x.getId(), x.getMaintainedAt(), x.getMaintainer(),
                    x.getTitle(), x.getDescription(), x.getCost());
        }
    }

    public record NoteItem(
            Long id, Long authorId, String content, LocalDateTime createdAt
    ) {
        public static NoteItem from(EquipmentNote x) {
            return new NoteItem(x.getId(), x.getAuthorId(), x.getContent(), x.getCreatedAt());
        }
    }
}
