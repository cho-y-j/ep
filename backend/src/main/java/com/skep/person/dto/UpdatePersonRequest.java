package com.skep.person.dto;

import com.skep.person.EmploymentType;
import com.skep.person.PersonRole;
import com.skep.person.PersonStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record UpdatePersonRequest(
        @Size(max = 100) String name,
        LocalDate birth,
        @Size(max = 32) String phone,
        Set<PersonRole> roles,
        @Size(max = 64) String employeeNo,
        @Size(max = 100) String jobTitle,
        @Size(max = 100) String team,
        @Size(max = 255) String qualification,
        @Size(max = 255) String address,
        @Email @Size(max = 255) String email,
        LocalDate hiredAt,
        PersonStatus status,
        EmploymentType employmentType,
        // 작업자 앱 로그인 계정 (선택) — 아이디 설정/변경, 비번 재설정.
        @Size(max = 64) String username,
        @Size(max = 100) String password
) {
}
