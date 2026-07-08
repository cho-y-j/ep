package com.skep.outgoing;

import com.skep.outgoing.dto.CreateOutgoingRequest;
import com.skep.outgoing.dto.OutgoingResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/outgoing-quotations")
public class OutgoingQuotationController {

    private final OutgoingQuotationService service;

    public OutgoingQuotationController(OutgoingQuotationService service) {
        this.service = service;
    }

    /** 공급사가 BP에게 영업 견적 발송. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OutgoingResponse send(@Valid @RequestBody CreateOutgoingRequest req,
                                  @CurrentUser AuthenticatedUser actor) {
        return service.send(req, actor);
    }

    /** 공급사 발신함. */
    @GetMapping("/sent")
    public List<OutgoingResponse> listSent(@CurrentUser AuthenticatedUser actor) {
        return service.listSent(actor);
    }

    /** BP 수신함. */
    @GetMapping("/inbox")
    public List<OutgoingResponse> listInbox(@CurrentUser AuthenticatedUser actor) {
        return service.listInbox(actor);
    }

    /** 단건 조회 — 발송 공급사 / 수신 BP / ADMIN. */
    @GetMapping("/{id}")
    public OutgoingResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    /** V37: BP 수락 사인. body.png_base64 + signer_name. */
    @PostMapping("/{id}/sign-bp")
    public OutgoingResponse signByBp(@PathVariable Long id,
                                      @RequestBody SignBody body,
                                      @CurrentUser AuthenticatedUser actor) {
        return service.signByBp(id, body.pngBase64(), body.signerName(), actor);
    }

    /** V37: BP 사인 PNG 이미지 조회. 발송 공급사 + 수신 BP 둘 다 가능. */
    @GetMapping("/{id}/bp-signature")
    public org.springframework.http.ResponseEntity<byte[]> bpSignature(@PathVariable Long id,
                                                                        @CurrentUser AuthenticatedUser actor) {
        byte[] png = service.getBpSignaturePng(id, actor);
        if (png == null || png.length == 0) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(png);
    }

    public record SignBody(
            @com.fasterxml.jackson.annotation.JsonProperty("png_base64") String pngBase64,
            @com.fasterxml.jackson.annotation.JsonProperty("signer_name") String signerName
    ) {}
}
