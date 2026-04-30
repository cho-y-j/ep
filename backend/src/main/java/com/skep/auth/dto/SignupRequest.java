package com.skep.auth.dto;

import com.skep.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 32) String phone,
        @NotNull Role role,
        @Size(max = 255) String companyName,
        @Size(max = 32) String businessNumber
) {
}
