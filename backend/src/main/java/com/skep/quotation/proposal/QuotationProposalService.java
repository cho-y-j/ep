package com.skep.quotation.proposal;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationLabels;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.quotation.*;
import com.skep.quotation.proposal.dto.CreateProposalRequest;
import com.skep.quotation.proposal.dto.ProposalResponse;
import com.skep.quotation.proposal.dto.UpdateProposalRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class QuotationProposalService {

    private final QuotationProposalRepository proposals;
    private final QuotationRequestRepository requests;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository users;
    private final NotificationService notifications;
    private final SiteRepository sites;
    private final com.skep.quotation.snapshot.ComparisonSnapshotService snapshotService;
    private final com.skep.quotation.dispatch.draft.DispatchDraftService dispatchDrafts;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QuotationProposalService.class);

    public QuotationProposalService(QuotationProposalRepository proposals,
                                     QuotationRequestRepository requests,
                                     EquipmentRepository equipmentRepo,
                                     PersonRepository personRepo,
                                     CompanyRepository companyRepo,
                                     UserRepository users,
                                     NotificationService notifications,
                                     SiteRepository sites,
                                     com.skep.quotation.snapshot.ComparisonSnapshotService snapshotService,
                                     com.skep.quotation.dispatch.draft.DispatchDraftService dispatchDrafts) {
        this.proposals = proposals;
        this.requests = requests;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.companyRepo = companyRepo;
        this.users = users;
        this.notifications = notifications;
        this.sites = sites;
        this.snapshotService = snapshotService;
        this.dispatchDrafts = dispatchDrafts;
    }

    /** qr 의 시각 친화 라벨 — "굴삭기 · 7/10~7/15 · 강남현장" */
    private String labelOf(QuotationRequest qr) {
        String siteName = qr.getSiteId() != null
                ? sites.findById(qr.getSiteId()).map(Site::getName).orElse(null) : null;
        return NotificationLabels.quotationLabel(qr, siteName);
    }

    // ── 공급사 작성 ────────────────────────────────────────

    public ProposalResponse submit(Long requestId, CreateProposalRequest req, AuthenticatedUser actor) {
        QuotationRequest qr = requestOrThrow(requestId);
        ensureOpenBid(qr);
        ensureSupplier(actor);
        if (!req.hasAnyRate()) {
            throw ApiException.badRequest("RATE_REQUIRED", "단가를 1개 이상 입력하세요");
        }
        if (req.equipmentId() != null && req.personId() != null) {
            throw ApiException.badRequest("AMBIGUOUS_RESOURCE", "장비/인원 중 하나만 지정");
        }
        if (qr.getStatus() != QuotationStatus.SENT) {
            throw ApiException.badRequest("REQUEST_NOT_OPEN", "수신 중인 견적이 아닙니다");
        }

        // 같은 공급사가 같은 견적에 이미 활성 제안 보유 시 추가 제출 차단.
        boolean alreadyActive = proposals.existsByRequestIdAndSupplierCompanyIdAndStatusIn(
                qr.getId(), actor.companyId(),
                java.util.List.of(QuotationProposalStatus.SUBMITTED, QuotationProposalStatus.PENDING_REVIEW));
        if (alreadyActive) {
            throw ApiException.badRequest("PROPOSAL_ALREADY_EXISTS", "이미 제출된 견적입니다");
        }

        // 자원 지정한 경우에만 자원 소유/일치 검증. 단가만 응찰이면 검증 스킵.
        if (req.equipmentId() != null) {
            Equipment e = equipmentRepo.findById(req.equipmentId()).orElseThrow(() ->
                    ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비 없음"));
            if (!e.getSupplierId().equals(actor.companyId())) {
                throw ApiException.forbidden("EQUIPMENT_NOT_OWNED", "본인 회사 장비만 제안 가능");
            }
            if (qr.getEquipmentCategory() != null && e.getCategory() != qr.getEquipmentCategory()) {
                throw ApiException.badRequest("EQUIPMENT_CATEGORY_MISMATCH",
                        "장비 카테고리가 견적과 다릅니다");
            }
            proposals.findByRequestIdAndSupplierCompanyIdAndEquipmentId(
                    qr.getId(), actor.companyId(), e.getId()).ifPresent(existing -> {
                throw ApiException.badRequest("PROPOSAL_DUPLICATE",
                        "이미 같은 장비로 제안된 견적입니다");
            });
        } else if (req.personId() != null) {
            Person p = personRepo.findById(req.personId()).orElseThrow(() ->
                    ApiException.badRequest("PERSON_NOT_FOUND", "인원 없음"));
            if (!p.getSupplierId().equals(actor.companyId())) {
                throw ApiException.forbidden("PERSON_NOT_OWNED", "본인 회사 인원만 제안 가능");
            }
            if (qr.getManpowerRole() != null
                    && (p.getRoles() == null || !p.getRoles().contains(qr.getManpowerRole()))) {
                throw ApiException.badRequest("PERSON_ROLE_MISMATCH",
                        "인원의 역할이 견적과 다릅니다");
            }
            proposals.findByRequestIdAndSupplierCompanyIdAndPersonId(
                    qr.getId(), actor.companyId(), p.getId()).ifPresent(existing -> {
                throw ApiException.badRequest("PROPOSAL_DUPLICATE",
                        "이미 같은 인원으로 제안된 견적입니다");
            });
        }

        QuotationProposal p = proposals.save(QuotationProposal.builder()
                .requestId(qr.getId())
                .supplierCompanyId(actor.companyId())
                .proposedByUserId(actor.id())
                .equipmentId(req.equipmentId())
                .personId(req.personId())
                .dailyRate(req.dailyRate())
                .otDailyRate(req.otDailyRate())
                .monthlyRate(req.monthlyRate())
                .otMonthlyRate(req.otMonthlyRate())
                .note(req.note())
                .dailyNote(req.dailyNote())
                .otDailyNote(req.otDailyNote())
                .monthlyNote(req.monthlyNote())
                .otMonthlyNote(req.otMonthlyNote())
                .build());

        // BP 에게 알림
        Company supplierCo = companyRepo.findById(actor.companyId()).orElse(null);
        String supplierLabel = supplierCo != null ? supplierCo.getName() : "공급사";
        notifications.sendToUser(qr.getRequestedByUserId(),
                NotificationType.QUOTATION_RECEIVED,
                "공개입찰 새 제안",
                supplierLabel + " 가 [" + labelOf(qr) + "] 공개입찰에 제안을 보냈습니다",
                "QUOTATION_PROPOSAL", p.getId(), qr.getSiteId());
        return toResponse(p);
    }

    public ProposalResponse update(Long proposalId, UpdateProposalRequest req, AuthenticatedUser actor) {
        QuotationProposal p = proposalOrThrow(proposalId);
        if (!p.getSupplierCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("UPDATE_DENIED", "본인 회사 제안만 수정 가능");
        }
        if (p.getStatus() == QuotationProposalStatus.FINAL_ACCEPTED
                || p.getStatus() == QuotationProposalStatus.REJECTED
                || p.getStatus() == QuotationProposalStatus.WITHDRAWN) {
            throw ApiException.badRequest("PROPOSAL_LOCKED",
                    "이미 처리된 제안은 수정 불가 (현재: " + p.getStatus() + ")");
        }
        p.updateOffer(req.dailyRate(), req.otDailyRate(), req.monthlyRate(), req.otMonthlyRate(), req.note(),
                req.dailyNote(), req.otDailyNote(), req.monthlyNote(), req.otMonthlyNote());
        return toResponse(p);
    }

    public ProposalResponse withdraw(Long proposalId, AuthenticatedUser actor) {
        QuotationProposal p = proposalOrThrow(proposalId);
        if (!p.getSupplierCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("WITHDRAW_DENIED", "본인 회사 제안만 철회 가능");
        }
        if (p.getStatus() == QuotationProposalStatus.FINAL_ACCEPTED) {
            throw ApiException.badRequest("PROPOSAL_FINALIZED",
                    "최종 수락된 제안은 철회 불가");
        }
        p.markWithdrawn();
        return toResponse(p);
    }

    // ── 조회 ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProposalResponse> listByRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requestOrThrow(requestId);
        // P2: BP는 회사 기준 — 같은 BP 회사 직원이면 전체 가능. 공급사는 자기 제안만.
        boolean isSameBp = qr.getBpCompanyId() != null
                && actor.companyId() != null
                && actor.companyId().equals(qr.getBpCompanyId());
        boolean canSeeAll = actor.role() == Role.ADMIN || (actor.role() == Role.BP && isSameBp);
        return proposals.findByRequestIdOrderByIdDesc(requestId).stream()
                .filter(p -> canSeeAll || p.getSupplierCompanyId().equals(actor.companyId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> listMine(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return proposals.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    // ── BP 최종 선정 ────────────────────────────────────

    /**
     * BP/ADMIN 가 제안 선정 → FINAL_ACCEPTED + 작업계획서에 자원 추가.
     * count 제한 — request.count 까지만 누적 선정 가능. 초과 시 거절.
     * close 는 별도 (cancelRequest).
     */
    public ProposalResponse finalize(Long proposalId, AuthenticatedUser actor) {
        // Audit-Fix #3 / H-5: race condition 방지 — 제안 행 + 요청 행 둘 다 비관적 락.
        // 제안 행만 잠그면 서로 다른 제안의 동시 finalize 가 같은 request.count 검사를 둘 다 통과해
        // 초과 선정될 수 있으므로, 요청 행을 잠가 같은 견적의 finalize 를 직렬화한다.
        QuotationProposal p = proposals.findByIdForUpdate(proposalId)
                .orElseThrow(() -> ApiException.notFound("PROPOSAL_NOT_FOUND", "제안 " + proposalId + " 없음"));
        QuotationRequest qr = requests.findByIdForUpdate(p.getRequestId())
                .orElseThrow(() -> ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + p.getRequestId() + " 없음"));
        ensureBpOrAdmin(actor, qr);
        if (p.getStatus() != QuotationProposalStatus.SUBMITTED
                && p.getStatus() != QuotationProposalStatus.PENDING_REVIEW) {
            throw ApiException.badRequest("PROPOSAL_NOT_FINALIZABLE",
                    "선정 가능 상태가 아닙니다 (현재: " + p.getStatus() + ")");
        }
        // 견적 상태 재검증 — close/cancel 된 견적은 finalize 불가
        if (qr.getStatus() != QuotationStatus.SENT) {
            throw ApiException.badRequest("REQUEST_NOT_OPEN",
                    "수신 중인 견적이 아닙니다 (현재: " + qr.getStatus() + ")");
        }
        long already = proposals.countByRequestIdAndStatus(qr.getId(), QuotationProposalStatus.FINAL_ACCEPTED);
        if (qr.getCount() != null && already >= qr.getCount()) {
            throw ApiException.badRequest("COUNT_FULL",
                    "요청 수량 (" + qr.getCount() + ") 만큼 이미 선정되었습니다");
        }

        // Site-A: 선정은 제안만 FINAL_ACCEPTED. 작업계획서/자원 첨부는 별도 단계(Site-D)에서.
        p.markFinalAccepted(actor.id(), null, null, null);

        // V80: 선정 직후 배차 초안 자동생성(OPEN_BID 제안 한정 — 제안은 OPEN_BID 에만 존재).
        //      별도 테이블이라 기존 dispatched 흐름과 격리. 실패해도 finalize 는 반드시 성공해야 하므로 예외를 삼킨다.
        try {
            dispatchDrafts.createFromFinalizedProposal(p, qr);
        } catch (Exception ex) {
            log.warn("배차 초안 생성 실패 (finalize 계속) proposalId={} requestId={}", p.getId(), qr.getId(), ex);
        }

        notifications.sendToCompany(p.getSupplierCompanyId(),
                NotificationType.QUOTATION_FINALIZED,
                "제안 최종 수락",
                "[" + labelOf(qr) + "] 공개입찰에 보낸 제안이 최종 선정되었습니다. 작업계획서는 BP가 별도로 작성합니다.",
                "QUOTATION_PROPOSAL", p.getId(), qr.getSiteId());

        // 카운트 가득 차면 다른 제안 자동 거절
        long acceptedNow = proposals.countByRequestIdAndStatus(qr.getId(), QuotationProposalStatus.FINAL_ACCEPTED);
        if (qr.getCount() != null && acceptedNow >= qr.getCount()) {
            rejectRemaining(qr);
        }

        // E-1: 선정 시점 비교증거 동결 (단일 snapshot 갱신)
        snapshotService.captureForRequest(qr.getId(), p.getId(), actor.id(), null);

        return toResponse(p);
    }

    /** BP 가 견적 close — 남은 제안 자동 거절. */
    public void closeRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requestOrThrow(requestId);
        ensureBpOrAdmin(actor, qr);
        rejectRemaining(qr);
        qr.markClosed();
    }

    /** BP 가 spec 수정 시 모든 활성 제안을 PENDING_REVIEW 로 — 공급사 재확인 알림. */
    public void markAllPendingReview(Long requestId) {
        QuotationRequest qr = requestOrThrow(requestId);
        for (QuotationProposal p : proposals.findByRequestIdOrderByIdDesc(qr.getId())) {
            if (p.getStatus() == QuotationProposalStatus.SUBMITTED) {
                p.markPendingReview();
                notifications.sendToCompany(p.getSupplierCompanyId(),
                        NotificationType.QUOTATION_RECEIVED,
                        "견적 내용 변경",
                        "[" + labelOf(qr) + "] 견적 내용이 BP에 의해 수정되었습니다. 재확인이 필요합니다",
                        "QUOTATION_PROPOSAL", p.getId(), qr.getSiteId());
            }
        }
    }

    // ── helpers ────────────────────────────────────────

    private void rejectRemaining(QuotationRequest qr) {
        for (QuotationProposal p : proposals.findByRequestIdOrderByIdDesc(qr.getId())) {
            if (p.getStatus() == QuotationProposalStatus.SUBMITTED
                    || p.getStatus() == QuotationProposalStatus.PENDING_REVIEW) {
                p.markRejected();
                notifications.sendToCompany(p.getSupplierCompanyId(),
                        NotificationType.QUOTATION_REJECTED,
                        "제안 거절",
                        "[" + labelOf(qr) + "] 공개입찰에 보낸 제안이 거절되었습니다 (다른 제안 선정/견적 종료)",
                        "QUOTATION_PROPOSAL", p.getId(), qr.getSiteId());
            }
        }
    }

    private QuotationProposal proposalOrThrow(Long id) {
        return proposals.findById(id).orElseThrow(() ->
                ApiException.notFound("PROPOSAL_NOT_FOUND", "제안 " + id + " 없음"));
    }

    /** Proposal 단건 조회 + 권한 검증. BP/ADMIN(견적의 BP) 또는 본인 공급사. */
    @Transactional(readOnly = true)
    public QuotationProposal getForView(Long proposalId, AuthenticatedUser actor) {
        QuotationProposal p = proposalOrThrow(proposalId);
        if (actor.role() == Role.ADMIN) return p;
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() != null && actor.companyId().equals(p.getSupplierCompanyId())) return p;
            throw ApiException.forbidden("PROPOSAL_VIEW_DENIED", "본인 회사 제안만 조회");
        }
        if (actor.role() == Role.BP) {
            QuotationRequest qr = requestOrThrow(p.getRequestId());
            boolean sameBp = (qr.getBpCompanyId() != null && qr.getBpCompanyId().equals(actor.companyId()))
                    || (qr.getOnBehalfOfBpCompanyId() != null && qr.getOnBehalfOfBpCompanyId().equals(actor.companyId()));
            if (sameBp) return p;
            throw ApiException.forbidden("PROPOSAL_VIEW_DENIED", "본인 견적에 대한 제안만 조회");
        }
        throw ApiException.forbidden("PROPOSAL_VIEW_DENIED", "권한 없음");
    }

    private QuotationRequest requestOrThrow(Long id) {
        return requests.findById(id).orElseThrow(() ->
                ApiException.notFound("QUOTATION_NOT_FOUND", "견적 " + id + " 없음"));
    }

    private void ensureOpenBid(QuotationRequest qr) {
        if (qr.getMode() != QuotationMode.OPEN_BID) {
            throw ApiException.badRequest("NOT_OPEN_BID", "공개입찰 견적이 아닙니다");
        }
    }

    private void ensureSupplier(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 제안 가능");
        }
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "회사 미식별");
        }
    }

    private void ensureBpOrAdmin(AuthenticatedUser actor, QuotationRequest qr) {
        if (actor.role() == Role.ADMIN) return;
        // P2: 회사 기준으로 변경 — 같은 BP 회사 다른 직원도 처리 가능
        if (actor.role() == Role.BP && actor.companyId() != null
                && qr.getBpCompanyId() != null
                && actor.companyId().equals(qr.getBpCompanyId())) return;
        throw ApiException.forbidden("NOT_REQUEST_OWNER", "본인 회사 견적이 아닙니다");
    }

    private ProposalResponse toResponse(QuotationProposal p) {
        Company supplier = companyRepo.findById(p.getSupplierCompanyId()).orElse(null);
        String eqLabel = null, ppLabel = null;
        if (p.getEquipmentId() != null) {
            Equipment e = equipmentRepo.findById(p.getEquipmentId()).orElse(null);
            if (e != null) {
                eqLabel = e.getVehicleNo() != null ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel() : "장비#" + e.getId());
            }
        }
        if (p.getPersonId() != null) {
            Person pp = personRepo.findById(p.getPersonId()).orElse(null);
            if (pp != null) ppLabel = pp.getName();
        }
        QuotationRequest qr = requests.findById(p.getRequestId()).orElse(null);
        String bpName = qr != null && qr.getBpCompanyId() != null
                ? companyRepo.findById(qr.getBpCompanyId()).map(Company::getName).orElse(null)
                : null;
        String requesterName = qr != null && qr.getRequestedByUserId() != null
                ? users.findById(qr.getRequestedByUserId()).map(com.skep.user.User::getName).orElse(null)
                : null;
        return ProposalResponse.from(p, supplier != null ? supplier.getName() : null,
                eqLabel, ppLabel, qr, bpName, requesterName);
    }
}
