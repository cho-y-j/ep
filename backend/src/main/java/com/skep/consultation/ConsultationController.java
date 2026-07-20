package com.skep.consultation;

import com.skep.consultation.dto.ConsultationResponse;
import com.skep.consultation.dto.CreateConsultationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService service;

    /** 공개(permitAll) — 비로그인 랜딩 방문자 접수. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse create(@Valid @RequestBody CreateConsultationRequest req) {
        return service.create(req);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ConsultationResponse> list() {
        return service.list();
    }

    @PatchMapping("/{id}/handle")
    @PreAuthorize("hasRole('ADMIN')")
    public ConsultationResponse handle(@PathVariable Long id) {
        return service.handle(id);
    }
}
