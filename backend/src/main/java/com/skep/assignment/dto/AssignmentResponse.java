package com.skep.assignment.dto;

import com.skep.assignment.EquipmentAssignment;
import com.skep.assignment.PersonAssignment;

import java.time.LocalDateTime;

/** 배치 이력 1건 응답. 자원 종류와 무관하게 같은 모양으로 내려간다. */
public record AssignmentResponse(
        Long id,
        Long resourceId,           // equipmentId 또는 personId
        Long siteId,
        String siteName,           // join 결과
        LocalDateTime assignedAt,
        LocalDateTime releasedAt,
        Long assignedBy,
        Long releasedBy,
        String note,
        String releaseReason,
        boolean active
) {
    public static AssignmentResponse fromEquipment(EquipmentAssignment a, String siteName) {
        return new AssignmentResponse(
                a.getId(),
                a.getEquipmentId(),
                a.getSiteId(),
                siteName,
                a.getAssignedAt(),
                a.getReleasedAt(),
                a.getAssignedBy(),
                a.getReleasedBy(),
                a.getNote(),
                a.getReleaseReason(),
                a.isActive()
        );
    }

    public static AssignmentResponse fromPerson(PersonAssignment a, String siteName) {
        return new AssignmentResponse(
                a.getId(),
                a.getPersonId(),
                a.getSiteId(),
                siteName,
                a.getAssignedAt(),
                a.getReleasedAt(),
                a.getAssignedBy(),
                a.getReleasedBy(),
                a.getNote(),
                a.getReleaseReason(),
                a.isActive()
        );
    }
}
