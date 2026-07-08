package com.skep.person.dto;

import com.skep.company.CompanyType;
import com.skep.person.EmploymentType;
import com.skep.person.Person;
import com.skep.person.PersonAssignmentStatus;
import com.skep.person.PersonRole;
import com.skep.person.PersonStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record PersonResponse(
        Long id,
        Long supplierId,
        String supplierName,
        CompanyType supplierType,
        String name,
        LocalDate birth,
        String phone,
        Set<PersonRole> roles,
        String employeeNo,
        String jobTitle,
        String team,
        String qualification,
        String address,
        String email,
        String username,
        LocalDate hiredAt,
        PersonStatus status,
        EmploymentType employmentType,
        // V11 배치 정보
        Long currentSiteId,
        String currentSiteName,
        PersonAssignmentStatus assignmentStatus,
        LocalDateTime lastAssignedAt,
        // ---
        boolean hasPhoto,
        long expiringCount,
        long documentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PersonResponse from(Person p) {
        return from(p, 0L, 0L, null, null, null);
    }

    public static PersonResponse from(Person p, long expiringCount) {
        return from(p, expiringCount, 0L, null, null, null);
    }

    public static PersonResponse from(Person p, long expiringCount, long documentCount) {
        return from(p, expiringCount, documentCount, null, null, null);
    }

    public static PersonResponse from(Person p, long expiringCount, long documentCount, String currentSiteName) {
        return from(p, expiringCount, documentCount, currentSiteName, null, null);
    }

    public static PersonResponse from(Person p, long expiringCount, long documentCount, String currentSiteName, String supplierName) {
        return from(p, expiringCount, documentCount, currentSiteName, supplierName, null);
    }

    public static PersonResponse from(Person p, long expiringCount, long documentCount, String currentSiteName, String supplierName, CompanyType supplierType) {
        return new PersonResponse(
                p.getId(),
                p.getSupplierId(),
                supplierName,
                supplierType,
                p.getName(),
                p.getBirth(),
                p.getPhone(),
                p.getRoles(),
                p.getEmployeeNo(),
                p.getJobTitle(),
                p.getTeam(),
                p.getQualification(),
                p.getAddress(),
                p.getEmail(),
                p.getUsername(),
                p.getHiredAt(),
                p.getStatus(),
                p.getEmploymentType(),
                p.getCurrentSiteId(),
                currentSiteName,
                p.getAssignmentStatus(),
                p.getLastAssignedAt(),
                p.getPhotoKey() != null,
                expiringCount,
                documentCount,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
