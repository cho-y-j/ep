package com.skep.person;

import com.skep.assignment.AssignmentService;
import com.skep.common.ApiException;
import com.skep.common.PageResponse;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.person.dto.CreatePersonRequest;
import com.skep.person.dto.PersonResponse;
import com.skep.person.dto.UpdatePersonRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService service;
    private final AssignmentService assignmentService;
    private final CompanyRepository companies;

    public PersonController(PersonService service, AssignmentService assignmentService, CompanyRepository companies) {
        this.service = service;
        this.assignmentService = assignmentService;
        this.companies = companies;
    }

    @GetMapping
    public PageResponse<PersonResponse> list(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PersonRole role,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var pg = service.search(actor, supplierId, role, q, pageable);
        var ids = pg.getContent().stream().map(Person::getId).toList();
        var expiring = service.expiringCountsByPersonIds(ids);
        var totals = service.documentCountsByPersonIds(ids);
        var siteNames = assignmentService.siteNamesByIds(
                pg.getContent().stream().map(Person::getCurrentSiteId).toList()
        );
        var supplierIds = pg.getContent().stream()
                .map(Person::getSupplierId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> supplierNames = Map.of();
        Map<Long, com.skep.company.CompanyType> supplierTypes = Map.of();
        if (!supplierIds.isEmpty()) {
            var cos = companies.findAllById(supplierIds);
            supplierNames = cos.stream().collect(Collectors.toMap(Company::getId, Company::getName));
            supplierTypes = cos.stream().collect(Collectors.toMap(Company::getId, Company::getType));
        }
        final Map<Long, String> sNames = supplierNames;
        final Map<Long, com.skep.company.CompanyType> sTypes = supplierTypes;
        return PageResponse.of(pg, p -> PersonResponse.from(
                p,
                expiring.getOrDefault(p.getId(), 0L),
                totals.getOrDefault(p.getId(), 0L),
                p.getCurrentSiteId() != null ? siteNames.get(p.getCurrentSiteId()) : null,
                p.getSupplierId() != null ? sNames.get(p.getSupplierId()) : null,
                p.getSupplierId() != null ? sTypes.get(p.getSupplierId()) : null
        ));
    }

    @GetMapping("/{id}")
    public PersonResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Person p = service.get(id, actor);
        var ids = List.of(p.getId());
        long expiring = service.expiringCountsByPersonIds(ids).getOrDefault(p.getId(), 0L);
        long total = service.documentCountsByPersonIds(ids).getOrDefault(p.getId(), 0L);
        String siteName = assignmentService.resolveSiteName(p.getCurrentSiteId());
        String supplierName = null;
        com.skep.company.CompanyType supplierType = null;
        if (p.getSupplierId() != null) {
            var co = companies.findById(p.getSupplierId()).orElse(null);
            if (co != null) {
                supplierName = co.getName();
                supplierType = co.getType();
            }
        }
        return PersonResponse.from(p, expiring, total, siteName, supplierName, supplierType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonResponse create(@Valid @RequestBody CreatePersonRequest req, @CurrentUser AuthenticatedUser actor) {
        return PersonResponse.from(service.create(req, actor));
    }

    @PatchMapping("/{id}")
    public PersonResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePersonRequest req, @CurrentUser AuthenticatedUser actor) {
        return PersonResponse.from(service.update(id, req, actor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<Void> bulkDelete(@RequestBody BulkIdsRequest req, @CurrentUser AuthenticatedUser actor) {
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            throw ApiException.badRequest("EMPTY_IDS", "ids 가 비어있습니다");
        }
        for (Long id : req.ids()) {
            service.delete(id, actor);
        }
        return ResponseEntity.noContent().build();
    }

    public record BulkIdsRequest(List<Long> ids) {}

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PersonResponse uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser actor
    ) {
        return PersonResponse.from(service.uploadPhoto(id, file, actor));
    }

    @DeleteMapping("/{id}/photo")
    public PersonResponse deletePhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return PersonResponse.from(service.deletePhoto(id, actor));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        PersonService.PhotoData data = service.loadPhoto(id, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType() != null ? data.contentType() : "application/octet-stream"))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(data.resource());
    }
}
