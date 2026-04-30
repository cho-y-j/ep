package com.skep.person;

import com.skep.common.ApiException;
import com.skep.common.PageResponse;
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

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
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
        return PageResponse.of(service.search(actor, supplierId, role, q, pageable), PersonResponse::from);
    }

    @GetMapping("/{id}")
    public PersonResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return PersonResponse.from(service.get(id, actor));
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
