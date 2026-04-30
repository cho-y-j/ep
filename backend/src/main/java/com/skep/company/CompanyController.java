package com.skep.company;

import com.skep.company.dto.CompanyResponse;
import com.skep.company.dto.CreateCompanyRequest;
import com.skep.company.dto.UpdateCompanyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CompanyResponse> list(@RequestParam(required = false) CompanyType type) {
        List<Company> result = type == null ? service.listAll() : service.listByType(type);
        return result.stream().map(CompanyResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable Long id) {
        return CompanyResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CreateCompanyRequest req) {
        return CompanyResponse.from(service.create(req.name(), req.businessNumber(), req.type()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCompanyRequest req) {
        return CompanyResponse.from(service.rename(id, req.name()));
    }
}
