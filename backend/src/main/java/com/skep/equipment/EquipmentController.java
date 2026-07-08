package com.skep.equipment;

import com.skep.assignment.AssignmentService;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.person.PersonRepository;
import com.skep.equipment.dto.CreateEquipmentRequest;
import com.skep.equipment.dto.EquipmentResponse;
import com.skep.equipment.dto.RegisterOperatorRequest;
import com.skep.equipment.dto.UpdateEquipmentRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService service;
    private final AssignmentService assignmentService;
    private final CompanyRepository companies;
    private final PersonRepository persons;

    public EquipmentController(EquipmentService service, AssignmentService assignmentService,
                               CompanyRepository companies, PersonRepository persons) {
        this.service = service;
        this.assignmentService = assignmentService;
        this.companies = companies;
        this.persons = persons;
    }

    @GetMapping
    public List<EquipmentResponse> list(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) EquipmentCategory category
    ) {
        var list = service.list(actor, supplierId, category);
        var ids = list.stream().map(Equipment::getId).toList();
        var counts = service.expiringCountsByEquipmentIds(ids);
        var siteNames = assignmentService.siteNamesByIds(
                list.stream().map(Equipment::getCurrentSiteId).toList()
        );
        var supplierIds = list.stream().map(Equipment::getSupplierId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> supplierNames = supplierIds.isEmpty()
                ? Map.of()
                : companies.findAllById(supplierIds).stream()
                        .collect(Collectors.toMap(Company::getId, Company::getName));
        return list.stream()
                .map(e -> EquipmentResponse.from(
                        e,
                        counts.getOrDefault(e.getId(), 0L),
                        e.getCurrentSiteId() != null ? siteNames.get(e.getCurrentSiteId()) : null,
                        e.getSupplierId() != null ? supplierNames.get(e.getSupplierId()) : null
                ))
                .toList();
    }

    @GetMapping("/{id}")
    public EquipmentResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Equipment e = service.get(id, actor);
        long expiring = service.expiringCountsByEquipmentIds(List.of(e.getId())).getOrDefault(e.getId(), 0L);
        String siteName = assignmentService.resolveSiteName(e.getCurrentSiteId());
        String supplierName = e.getSupplierId() == null
                ? null
                : companies.findById(e.getSupplierId()).map(Company::getName).orElse(null);
        return EquipmentResponse.from(e, expiring, siteName, supplierName);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentResponse create(@Valid @RequestBody CreateEquipmentRequest req, @CurrentUser AuthenticatedUser actor) {
        return EquipmentResponse.from(service.create(req, actor));
    }

    @PatchMapping("/{id}")
    public EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEquipmentRequest req, @CurrentUser AuthenticatedUser actor) {
        Equipment e = service.update(id, req, actor);
        return EquipmentResponse.from(e, 0L, assignmentService.resolveSiteName(e.getCurrentSiteId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
        return ResponseEntity.noContent().build();
    }

    /** Phase4: 외부 장비 기사(조종원) 등록 + 로그인 계정 발급 + 장비 연결. */
    @PostMapping("/{id}/operator")
    public EquipmentResponse registerOperator(@PathVariable Long id, @Valid @RequestBody RegisterOperatorRequest req, @CurrentUser AuthenticatedUser actor) {
        Equipment e = service.registerOperator(id, req, actor);
        return EquipmentResponse.from(e, 0L, assignmentService.resolveSiteName(e.getCurrentSiteId()));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EquipmentResponse uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @CurrentUser AuthenticatedUser actor
    ) {
        Equipment e = service.uploadPhoto(id, file, actor);
        return EquipmentResponse.from(e, 0L, assignmentService.resolveSiteName(e.getCurrentSiteId()));
    }

    @DeleteMapping("/{id}/photo")
    public EquipmentResponse deletePhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Equipment e = service.deletePhoto(id, actor);
        return EquipmentResponse.from(e, 0L, assignmentService.resolveSiteName(e.getCurrentSiteId()));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getPhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        EquipmentService.PhotoData data = service.loadPhoto(id, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType() != null ? data.contentType() : "application/octet-stream"))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(data.resource());
    }

    /** V36: 장비 기본 조종원 목록 — 우선순위 오름차순. */
    @GetMapping("/{id}/default-operators")
    public List<DefaultOperatorItem> listDefaultOperators(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        var rows = service.listDefaultOperators(id, actor);
        var nameMap = persons.findAllById(rows.stream().map(EquipmentDefaultOperator::getPersonId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getId(), p -> p.getName()));
        return rows.stream()
                .map(o -> new DefaultOperatorItem(o.getId(), o.getPersonId(), nameMap.get(o.getPersonId()), o.getPriority()))
                .toList();
    }

    /** V36: 장비 기본 조종원 일괄 갱신. body.person_ids 순서대로 priority 1,2,3... */
    @PutMapping("/{id}/default-operators")
    public List<DefaultOperatorItem> setDefaultOperators(@PathVariable Long id,
                                                          @RequestBody SetDefaultOperatorsRequest req,
                                                          @CurrentUser AuthenticatedUser actor) {
        var rows = service.setDefaultOperators(id, req.personIds(), actor);
        var nameMap = persons.findAllById(rows.stream().map(EquipmentDefaultOperator::getPersonId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getId(), p -> p.getName()));
        return rows.stream()
                .map(o -> new DefaultOperatorItem(o.getId(), o.getPersonId(), nameMap.get(o.getPersonId()), o.getPriority()))
                .toList();
    }

    public record SetDefaultOperatorsRequest(
            @com.fasterxml.jackson.annotation.JsonProperty("person_ids") List<Long> personIds) {}

    public record DefaultOperatorItem(Long id, Long personId, String personName, Integer priority) {}
}
