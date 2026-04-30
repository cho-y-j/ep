package com.skep.person;

import com.skep.person.dto.CreatePersonRequest;
import com.skep.person.dto.PersonResponse;
import com.skep.person.dto.UpdatePersonRequest;
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

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
    }

    @GetMapping
    public List<PersonResponse> list(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PersonRole role
    ) {
        return service.list(actor, supplierId, role).stream().map(PersonResponse::from).toList();
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
