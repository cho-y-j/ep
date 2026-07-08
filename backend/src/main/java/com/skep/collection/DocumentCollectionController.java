package com.skep.collection;

import com.skep.collection.dto.CollectionDtos;
import com.skep.document.OwnerType;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 서류 수집 링크 — 작성자(BP/공급사/ADMIN) API. */
@RestController
@RequestMapping("/api/document-collections")
public class DocumentCollectionController {

    private final DocumentCollectionService service;

    public DocumentCollectionController(DocumentCollectionService service) {
        this.service = service;
    }

    @PostMapping
    public CollectionDtos.Response create(@RequestBody CollectionDtos.CreateRequest req, @CurrentUser AuthenticatedUser actor) {
        return service.create(req, actor);
    }

    /** 목록 — ownerType+ownerId 주면 해당 자원 것만, 없으면 내 회사 전체. */
    @GetMapping
    public List<CollectionDtos.Response> list(
            @RequestParam(required = false) OwnerType ownerType,
            @RequestParam(required = false) Long ownerId,
            @CurrentUser AuthenticatedUser actor) {
        if (ownerType != null && ownerId != null) return service.listByOwner(ownerType, ownerId, actor);
        return service.list(actor);
    }

    @GetMapping("/{id}")
    public CollectionDtos.Response get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    /** 수집된 서류를 순서대로 PDF로 합쳐 이메일 발송. */
    @PostMapping("/{id}/send")
    public CollectionDtos.Response send(@PathVariable Long id, @RequestBody(required = false) CollectionDtos.SendPdfRequest req,
                                        @CurrentUser AuthenticatedUser actor) {
        return service.compileAndSend(id, req, actor);
    }

    /** 공개 링크를 받는사람 휴대폰으로 문자(SMS) 발송. */
    @PostMapping("/{id}/send-link")
    public com.skep.alimtalk.AlimTalkService.SendResult sendLink(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.sendLink(id, actor);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.cancel(id, actor);
        return ResponseEntity.noContent().build();
    }
}
