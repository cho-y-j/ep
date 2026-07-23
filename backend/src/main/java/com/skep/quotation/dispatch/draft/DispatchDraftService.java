package com.skep.quotation.dispatch.draft;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.quotation.QuotationMode;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.QuotationRequestType;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.quotation.dispatch.DispatchedEquipmentService;
import com.skep.quotation.dispatch.DispatchedPersonRepository;
import com.skep.quotation.dispatch.DispatchedPersonService;
import com.skep.quotation.dispatch.dto.DispatchPersonRequest;
import com.skep.quotation.dispatch.dto.DispatchRequest;
import com.skep.quotation.proposal.QuotationProposal;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 배차 초안(V80) — 선정 직후 자동 생성, 공급사 confirm 으로 기존 send() 재사용해 실제 dispatched 생성.
 * 기존 dispatched 리더/수동 send 경로와 격리(별도 테이블).
 */
@Service
@RequiredArgsConstructor
public class DispatchDraftService {

    private final DispatchDraftRepository drafts;
    private final QuotationRequestRepository requests;
    private final DispatchedEquipmentRepository dispatchedEq;
    private final DispatchedPersonRepository dispatchedPerson;
    private final DispatchedEquipmentService equipmentSend;
    private final DispatchedPersonService personSend;
    private final CompanyService companyService;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final CompanyRepository companies;

    /**
     * finalize 훅 — 선정된 제안의 자원+단가로 초안 1건 생성. OPEN_BID 경로에서만 호출.
     * 트랜잭션 경계를 새로 만들지 않는다(@Transactional 없음): finalize 의 열린 트랜잭션에 그대로 참여.
     *  - finalize 는 request/proposal 행을 PESSIMISTIC_WRITE(FOR UPDATE) 로 잠근 상태다. 같은 트랜잭션이라
     *    draft 의 FK(FOR KEY SHARE)가 자기 자신의 FOR UPDATE 와 충돌하지 않아 교착 없음.
     *    (REQUIRES_NEW 로 분리하면 별도 트랜잭션의 FK 락이 finalize 의 FOR UPDATE 와 충돌해 self-deadlock.)
     *  - 여기에 @Transactional 을 붙이면 join 한 트랜잭션이 예외 시 rollback-only 로 마킹돼 호출측 try/catch 를
     *    무력화하고 finalize commit 이 UnexpectedRollbackException 으로 깨진다. 그래서 경계를 두지 않는다.
     *  - dedup 검사 후 저장하므로 정상 데이터에선 예외가 나지 않는다. 로직 예외는 호출측이 삼켜 finalize 를 지킨다.
     */
    public void createFromFinalizedProposal(QuotationProposal p, QuotationRequest qr) {
        // OPEN_BID 경로만. TARGETED 는 배차 불가 제약이므로 초안도 만들지 않는다.
        if (qr.getMode() != QuotationMode.OPEN_BID) return;
        Long requestId = p.getRequestId();
        Long supplier = p.getSupplierCompanyId();

        DispatchDraftResourceType type;
        Long equipmentId = null;
        Long personId = null;
        if (p.getEquipmentId() != null) {
            type = DispatchDraftResourceType.EQUIPMENT;
            equipmentId = p.getEquipmentId();
        } else if (p.getPersonId() != null) {
            type = DispatchDraftResourceType.PERSON;
            personId = p.getPersonId();
        } else {
            // 단가-only 제안(V48). EQUIPMENT 요청이면 equipment_id=null 초안(V45 선례), MANPOWER 는 skip(person_id NOT NULL).
            if (qr.getRequestType() == QuotationRequestType.MANPOWER) return;
            type = DispatchDraftResourceType.EQUIPMENT;
        }

        // 이미 실제 dispatched 가 있으면 초안 불필요(중복 방지).
        boolean alreadyDispatched = type == DispatchDraftResourceType.EQUIPMENT
                ? dispatchedEq.existsByQuotationRequestIdAndSupplierCompanyId(requestId, supplier)
                : dispatchedPerson.existsByQuotationRequestIdAndSupplierCompanyId(requestId, supplier);
        if (alreadyDispatched) return;

        // 이미 초안이 있으면(어떤 상태든) 재생성 금지.
        if (drafts.existsByQuotationRequestIdAndSupplierCompanyId(requestId, supplier)) return;

        drafts.save(DispatchDraft.builder()
                .quotationRequestId(requestId)
                .supplierCompanyId(supplier)
                .resourceType(type)
                .equipmentId(equipmentId)
                .personId(personId)
                .dailyPrice(toLong(p.getDailyRate()))
                .monthlyPrice(toLong(p.getMonthlyRate()))
                .otDailyPrice(toLong(p.getOtDailyRate()))
                .otMonthlyPrice(toLong(p.getOtMonthlyRate()))
                .notes(p.getNote())
                .sourceProposalId(p.getId())
                .build());
    }

    /** GET /api/quotations/{id}/dispatch-drafts — 공급사=본인/직속자식 분, BP/ADMIN=전체. 미확정(DRAFT)만. */
    @Transactional(readOnly = true)
    public List<DispatchDraftResponse> listByRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        boolean canSeeAll = actor.role() == Role.ADMIN
                || (actor.role() == Role.BP && actor.companyId() != null
                    && (actor.companyId().equals(qr.getBpCompanyId())
                        || actor.companyId().equals(qr.getOnBehalfOfBpCompanyId())));
        List<DispatchDraft> list;
        if (canSeeAll) {
            list = drafts.findByQuotationRequestIdAndStatus(requestId, DispatchDraftStatus.DRAFT);
        } else if (actor.companyId() != null) {
            list = drafts.findByQuotationRequestIdAndSupplierCompanyIdInAndStatus(
                    requestId, companyService.selfAndChildren(actor.companyId()), DispatchDraftStatus.DRAFT);
        } else {
            list = List.of();
        }
        return list.stream().map(this::toResponse).toList();
    }

    /**
     * POST /api/quotations/dispatch-drafts/{draftId}/confirm — 초안을 기존 send() 로 실제 발송.
     * 선정검증·멱등 409·V77 sub-supplier 귀속·BP 알림 전부 send() 재사용. 성공 시 초안 CONFIRMED.
     */
    @Transactional
    public DispatchDraftResponse confirm(Long draftId, AuthenticatedUser actor) {
        DispatchDraft d = drafts.findById(draftId)
                .orElseThrow(() -> ApiException.notFound("DRAFT_NOT_FOUND", "초안 " + draftId + " 없음"));
        ensureCanConfirm(d, actor);
        if (d.getResourceType() == DispatchDraftResourceType.EQUIPMENT) {
            equipmentSend.send(d.getQuotationRequestId(), toEquipmentRequest(d), actor);
        } else {
            personSend.send(d.getQuotationRequestId(), toPersonRequest(d), actor);
        }
        // send() 말미의 discard 가 DRAFT→DISCARDED 로 바꾸지만, 여기서 CONFIRMED 로 덮어써 최종 상태를 확정.
        d.markConfirmed();
        return toResponse(d);
    }

    // ── helpers ────────────────────────────────────────

    private void ensureCanConfirm(DispatchDraft d, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Long cid = actor.companyId();
        // 초안 소유 공급사(본인) 또는 대행 부모(V77: selfAndChildren 에 초안 공급사 포함).
        if (cid != null && companyService.selfAndChildren(cid).contains(d.getSupplierCompanyId())) return;
        throw ApiException.forbidden("DRAFT_CONFIRM_DENIED", "본인 초안만 확정할 수 있습니다");
    }

    private DispatchRequest toEquipmentRequest(DispatchDraft d) {
        if (d.getEquipmentId() != null) {
            var item = new DispatchRequest.Item(d.getEquipmentId(),
                    d.getDailyPrice(), d.getOtDailyPrice(), d.getMonthlyPrice(), d.getOtMonthlyPrice(),
                    d.getNotes(), null, null, null, null);
            return new DispatchRequest(List.of(item), null, null, null, null, d.getNotes(), null, null, null, null);
        }
        // 단가-only(자원 미지정) → send() 의 rate 모드로 equipment_id=null 1행.
        return new DispatchRequest(null,
                d.getDailyPrice(), d.getOtDailyPrice(), d.getMonthlyPrice(), d.getOtMonthlyPrice(),
                d.getNotes(), null, null, null, null);
    }

    private DispatchPersonRequest toPersonRequest(DispatchDraft d) {
        var item = new DispatchPersonRequest.Item(d.getPersonId(),
                d.getDailyPrice(), d.getOtDailyPrice(), d.getMonthlyPrice(), d.getOtMonthlyPrice(), d.getNotes(), null);
        return new DispatchPersonRequest(List.of(item), d.getNotes());
    }

    private DispatchDraftResponse toResponse(DispatchDraft d) {
        String supplierName = companies.findById(d.getSupplierCompanyId()).map(Company::getName).orElse(null);
        String eqLabel = null, eqCategory = null, personLabel = null, jobTitle = null;
        if (d.getEquipmentId() != null) {
            Equipment e = equipments.findById(d.getEquipmentId()).orElse(null);
            if (e != null) {
                eqLabel = e.getVehicleNo() != null ? e.getVehicleNo()
                        : (e.getModel() != null ? e.getModel() : "#" + e.getId());
                eqCategory = e.getCategory();
            }
        }
        if (d.getPersonId() != null) {
            Person p = persons.findById(d.getPersonId()).orElse(null);
            if (p != null) {
                personLabel = p.getName();
                jobTitle = p.getJobTitle();
            }
        }
        return DispatchDraftResponse.from(d, supplierName, eqLabel, eqCategory, personLabel, jobTitle);
    }

    private static Long toLong(Integer v) {
        return v != null ? v.longValue() : null;
    }
}
