package com.skep.site;

import com.skep.company.CompanyType;
import com.skep.company.dto.CompanyResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.site.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteService service;

    public SiteController(SiteService service) {
        this.service = service;
    }

    @GetMapping
    public List<SiteResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    @GetMapping("/{id}")
    public SiteResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SiteResponse create(@Valid @RequestBody CreateSiteRequest req, @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @PatchMapping("/{id}")
    public SiteResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSiteRequest req,
                               @CurrentUser AuthenticatedUser actor) {
        return service.update(id, req, actor);
    }

    @PostMapping("/{id}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public SiteResponse addParticipant(@PathVariable Long id, @Valid @RequestBody AddSiteParticipantRequest req,
                                       @CurrentUser AuthenticatedUser actor) {
        return service.addParticipant(id, req, actor);
    }

    @DeleteMapping("/{siteId}/participants/{participantId}")
    public SiteResponse removeParticipant(@PathVariable Long siteId, @PathVariable Long participantId,
                                          @CurrentUser AuthenticatedUser actor) {
        return service.removeParticipant(siteId, participantId, actor);
    }

    @GetMapping("/supplier-companies")
    public List<CompanyResponse> supplierCompanies(@RequestParam CompanyType type,
                                                   @CurrentUser AuthenticatedUser actor) {
        return service.supplierCompanies(type, actor).stream()
                .map(CompanyResponse::from)
                .toList();
    }
}
