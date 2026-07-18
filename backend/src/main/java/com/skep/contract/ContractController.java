package com.skep.contract;

import com.skep.contract.dto.ContractResponse;
import com.skep.contract.dto.SaveContractRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService service;

    @PostMapping
    public ContractResponse create(@Valid @RequestBody SaveContractRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @GetMapping
    public List<ContractResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    @GetMapping("/{id}")
    public ContractResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    @PutMapping("/{id}")
    public ContractResponse update(@PathVariable Long id, @Valid @RequestBody SaveContractRequest req,
                                   @CurrentUser AuthenticatedUser actor) {
        return service.update(id, req, actor);
    }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ContractResponse uploadFile(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file,
                                       @CurrentUser AuthenticatedUser actor) {
        return service.uploadFile(id, file, actor);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Resource r = service.loadFile(id, actor);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contract-" + id + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(r);
    }
}
