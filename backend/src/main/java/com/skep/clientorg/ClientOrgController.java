package com.skep.clientorg;

import com.skep.clientorg.dto.ClientOrgResponse;
import com.skep.clientorg.dto.CreateClientOrgRequest;
import com.skep.clientorg.dto.UpdateClientOrgRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client-orgs")
public class ClientOrgController {

    private final ClientOrgService service;

    public ClientOrgController(ClientOrgService service) {
        this.service = service;
    }

    /** BP 드롭다운 등 일반 사용자도 active 리스트 조회 가능. */
    @GetMapping
    public List<ClientOrgResponse> list() {
        return service.listActive();
    }

    /** ADMIN 관리 페이지용 — 비활성 포함 전체. */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ClientOrgResponse> listAll() {
        return service.listAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientOrgResponse create(@Valid @RequestBody CreateClientOrgRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientOrgResponse update(@PathVariable Long id, @Valid @RequestBody UpdateClientOrgRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientOrgResponse deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return service.listAll().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientOrgResponse activate(@PathVariable Long id) {
        service.activate(id);
        return service.listAll().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    /** 완전 삭제 — 자원 이력 참조 시 거절. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hardDelete(@PathVariable Long id) {
        service.hardDelete(id);
    }
}
