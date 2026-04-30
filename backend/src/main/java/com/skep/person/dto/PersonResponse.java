package com.skep.person.dto;

import com.skep.person.Person;
import com.skep.person.PersonRole;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record PersonResponse(
        Long id,
        Long supplierId,
        String name,
        LocalDate birth,
        String phone,
        Set<PersonRole> roles,
        boolean hasPhoto,
        LocalDateTime createdAt
) {
    public static PersonResponse from(Person p) {
        return new PersonResponse(
                p.getId(),
                p.getSupplierId(),
                p.getName(),
                p.getBirth(),
                p.getPhone(),
                p.getRoles(),
                p.getPhotoKey() != null,
                p.getCreatedAt()
        );
    }
}
