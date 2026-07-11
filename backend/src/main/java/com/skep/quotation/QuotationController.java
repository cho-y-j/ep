package com.skep.quotation;

import com.skep.equipment.EquipmentCategory;
import com.skep.person.PersonRole;
import com.skep.quotation.dto.*;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * S-10: 장비 견적 요청 API.
 */
@RestController
@RequestMapping("/api/quotations")
public class QuotationController {

    private final QuotationService service;

    public QuotationController(QuotationService service) {
        this.service = service;
    }

    /** Site-C: site 무관. 카테고리 매칭되는 전체 EQUIPMENT 공급사 장비 풀. */
    @GetMapping("/equipment-candidates")
    public List<QuotationCandidateResponse> equipmentCandidates(
            @RequestParam(required = false) EquipmentCategory category,
            @CurrentUser AuthenticatedUser actor) {
        return service.candidates(category, actor);
    }

    /** Site-C: site 무관. 역할 매칭 전체 MANPOWER 공급사 인원 풀. */
    @GetMapping("/manpower-candidates")
    public List<QuotationManpowerCandidateResponse> manpowerCandidates(
            @RequestParam(required = false) PersonRole role,
            @CurrentUser AuthenticatedUser actor) {
        return service.manpowerCandidates(role, actor);
    }

    /** 견적 요청 생성 (BP self 또는 ADMIN onBehalfOf). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuotationRequestResponse create(@Valid @RequestBody CreateQuotationRequest req,
                                            @CurrentUser AuthenticatedUser actor) {
        QuotationRequest qr = service.create(req, actor);
        return service.get(qr.getId(), actor);
    }

    /** V33: 공개입찰 생성 (site 없이, ClientOrg + workLocationText 옵션). */
    @PostMapping("/open-bid")
    @ResponseStatus(HttpStatus.CREATED)
    public QuotationRequestResponse createOpenBid(@Valid @RequestBody CreateOpenBidRequest req,
                                                    @CurrentUser AuthenticatedUser actor) {
        QuotationRequest qr = service.createOpenBid(req, actor);
        return service.get(qr.getId(), actor);
    }

    /** V33: 공급사 공개입찰 게시판. */
    @GetMapping("/open-bids")
    public List<QuotationRequestResponse> listOpenBids(@CurrentUser AuthenticatedUser actor) {
        return service.listOpenBids(actor);
    }

    /**
     * 현장 단위 묶음 발송 — 한 사이트에 장비 + 인력 N역할을 한 API 호출로 전송.
     * 인원 중복 (서로 다른 역할 행에 같은 person_id) 은 서버에서 차단.
     * 같은 bundle_id 부여, 한 트랜잭션.
     */
    @PostMapping("/bundle")
    @ResponseStatus(HttpStatus.CREATED)
    public List<QuotationRequestResponse> createBundle(@Valid @RequestBody CreateQuotationBundleRequest req,
                                                        @CurrentUser AuthenticatedUser actor) {
        return service.createBundle(req, actor).stream()
                .map(qr -> service.get(qr.getId(), actor))
                .toList();
    }

    /** 목록 — 역할별 가시성 적용. */
    @GetMapping
    public List<QuotationRequestResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    /** 목록 chip 용 단계 집계 — 보이는 견적 각각의 선정/배차/서류묶음 완료 여부(배치). */
    @GetMapping("/stage-summary")
    public List<QuotationStageSummaryResponse> stageSummary(@CurrentUser AuthenticatedUser actor) {
        return service.stageSummary(actor);
    }

    /** 상세. */
    @GetMapping("/{id}")
    public QuotationRequestResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.get(id, actor);
    }

    /** 공급사 응답 (수락/거부). */
    @PostMapping("/{id}/targets/{targetId}/respond")
    public QuotationRequestResponse respond(@PathVariable Long id,
                                              @PathVariable Long targetId,
                                              @Valid @RequestBody RespondQuotationTargetRequest req,
                                              @CurrentUser AuthenticatedUser actor) {
        return service.respond(id, targetId, req, actor);
    }

    /** BP/ADMIN 최종 수락 — WorkPlan 자원으로 반영 + 가격 저장. */
    @PostMapping("/{id}/targets/{targetId}/finalize")
    public QuotationRequestResponse finalize(@PathVariable Long id,
                                               @PathVariable Long targetId,
                                               @CurrentUser AuthenticatedUser actor) {
        return service.finalize(id, targetId, actor);
    }

    /** 묶음 목록 — 사용자 관점에서 "견적 1건" 단위. 같은 bundle_id 끼리 그룹핑. */
    @GetMapping("/bundles")
    public List<QuotationBundleResponse> listBundles(@CurrentUser AuthenticatedUser actor) {
        return service.listBundles(actor);
    }

    /** 묶음 상세 — 1 묶음 안의 모든 자원 명세 + 공급사 target 응답 포함. */
    @GetMapping("/bundles/{bundleId}")
    public QuotationBundleResponse getBundle(@PathVariable java.util.UUID bundleId,
                                              @CurrentUser AuthenticatedUser actor) {
        return service.getBundle(bundleId, actor);
    }

    /** 묶음 단위 취소 — bundle 안의 SENT 상태 모두 CANCELLED 로. */
    @PostMapping("/bundles/{bundleId}/cancel")
    public QuotationBundleResponse cancelBundle(@PathVariable java.util.UUID bundleId,
                                                 @CurrentUser AuthenticatedUser actor) {
        service.cancelBundle(bundleId, actor);
        return service.getBundle(bundleId, actor);
    }

    /** 묶음 단위 삭제 — bundle 안의 모든 row + targets 완전 제거. */
    @DeleteMapping("/bundles/{bundleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBundle(@PathVariable java.util.UUID bundleId,
                              @CurrentUser AuthenticatedUser actor) {
        service.deleteBundle(bundleId, actor);
    }

    /** BP/ADMIN 견적 취소 (CANCELLED 상태로 변경 — 이력 유지). */
    @PostMapping("/{id}/cancel")
    public QuotationRequestResponse cancel(@PathVariable Long id,
                                             @CurrentUser AuthenticatedUser actor) {
        return service.cancel(id, actor);
    }

    /** BP/ADMIN 견적 완전 삭제 — targets 함께 제거. FINAL_ACCEPTED 가 있으면 거부. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        service.delete(id, actor);
    }
}
