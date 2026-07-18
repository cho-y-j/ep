package com.skep.dailywork;

import com.skep.dailywork.dto.DailyWorkLogResponse;
import com.skep.dailywork.dto.SaveDailyWorkLogRequest;
import com.skep.dailywork.dto.SignWorkLogRequest;
import com.skep.dailywork.dto.WorkLogLedgerResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/daily-work-logs")
@RequiredArgsConstructor
public class DailyWorkLogController {

    private final DailyWorkLogService service;

    @PostMapping
    public DailyWorkLogResponse create(@Valid @RequestBody SaveDailyWorkLogRequest req,
                                       @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    @PutMapping("/{id}")
    public DailyWorkLogResponse update(@PathVariable Long id, @Valid @RequestBody SaveDailyWorkLogRequest req,
                                       @CurrentUser AuthenticatedUser actor) {
        return service.update(id, req, actor);
    }

    @GetMapping
    public List<DailyWorkLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long personId,
            @CurrentUser AuthenticatedUser actor) {
        return service.list(actor, from, to, equipmentId, personId);
    }

    @GetMapping("/ledger")
    public WorkLogLedgerResponse ledger(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) String siteName,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) String period,
            @CurrentUser AuthenticatedUser actor) {
        return service.ledger(actor, siteId, siteName, equipmentId, personId, period);
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DailyWorkLogResponse uploadPhoto(@PathVariable Long id,
                                            @RequestParam("file") MultipartFile file,
                                            @CurrentUser AuthenticatedUser actor) {
        return service.uploadPhoto(id, file, actor);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        Resource r = service.loadPhoto(id, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(r);
    }

    @PostMapping("/{id}/sign")
    public DailyWorkLogResponse sign(@PathVariable Long id, @Valid @RequestBody SignWorkLogRequest req,
                                     @CurrentUser AuthenticatedUser actor) {
        return service.sign(id, req.signaturePngBase64(), actor);
    }

    @GetMapping("/{id}/sign-image")
    public ResponseEntity<Resource> signImage(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        byte[] png = service.loadSignImage(id, actor);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new ByteArrayResource(png));
    }
}
