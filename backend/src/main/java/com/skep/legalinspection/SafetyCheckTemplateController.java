package com.skep.legalinspection;

import com.skep.legalinspection.dto.SafetyCheckTemplateDtos.Response;
import com.skep.legalinspection.dto.SafetyCheckTemplateDtos.SaveRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** S2′ 점검 템플릿 관리 — ADMIN 전용 CRUD(시스템 관리 그룹). */
@RestController
@RequestMapping("/api/safety-check-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SafetyCheckTemplateController {

    private final SafetyCheckTemplateService service;

    @GetMapping
    public List<Response> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public Response create(@Valid @RequestBody SaveRequest req, @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody SaveRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
