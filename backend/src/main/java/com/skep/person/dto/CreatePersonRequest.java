package com.skep.person.dto;

import com.skep.person.PersonRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record CreatePersonRequest(
        Long supplierId,
        @NotBlank @Size(max = 100) String name,
        LocalDate birth,
        @Size(max = 32) String phone,
        @NotEmpty Set<PersonRole> roles
) {
}
