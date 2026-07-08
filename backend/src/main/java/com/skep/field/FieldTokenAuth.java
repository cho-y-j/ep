package com.skep.field;

import com.skep.common.ApiException;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** X-Field-Token(attendance_code) 인증 — 워치/폰 field-auth 컨트롤러 공용. */
@Component
@RequiredArgsConstructor
public class FieldTokenAuth {

    private final PersonRepository personRepo;

    public Person authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.forbidden("NO_TOKEN", "토큰 없음");
        }
        return personRepo.findByAttendanceCode(token.trim()).orElseThrow(() ->
                ApiException.forbidden("INVALID_TOKEN", "토큰이 유효하지 않습니다"));
    }
}
