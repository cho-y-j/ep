package com.skep.assignment.dto;

import com.skep.person.Person;
import com.skep.person.PersonAssignmentStatus;
import com.skep.person.PersonRole;

import java.time.LocalDateTime;
import java.util.Set;

public record PersonCandidateResponse(
        Long id,
        Long supplierId,
        String supplierName,
        String name,
        Set<PersonRole> roles,
        String employeeNo,
        String jobTitle,
        boolean hasPhoto,
        PersonAssignmentStatus assignmentStatus,
        Long currentSiteId,
        String currentSiteName,
        LocalDateTime lastAssignedAt,
        boolean previouslyUsedOnSite,
        boolean currentlyAssigned,
        long expiringDocuments,
        long missingDocuments,
        boolean blocked
) {
    public static PersonCandidateResponse from(
            Person p,
            String supplierName,
            String currentSiteName,
            boolean previouslyUsedOnSite,
            long expiringDocuments,
            long missingDocuments
    ) {
        boolean currentlyAssigned = p.getCurrentSiteId() != null
                && p.getAssignmentStatus() == PersonAssignmentStatus.ON_DUTY;
        boolean blocked = p.getAssignmentStatus() == PersonAssignmentStatus.INACTIVE
                || missingDocuments > 0;
        return new PersonCandidateResponse(
                p.getId(),
                p.getSupplierId(),
                supplierName,
                p.getName(),
                p.getRoles(),
                p.getEmployeeNo(),
                p.getJobTitle(),
                p.getPhotoKey() != null,
                p.getAssignmentStatus(),
                p.getCurrentSiteId(),
                currentSiteName,
                p.getLastAssignedAt(),
                previouslyUsedOnSite,
                currentlyAssigned,
                expiringDocuments,
                missingDocuments,
                blocked
        );
    }
}
