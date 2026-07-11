package com.skep.quotation.dispatch;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.dispatch.dto.DispatchPersonRequest;
import com.skep.quotation.dispatch.dto.DispatchedPersonResponse;
import com.skep.quotation.proposal.QuotationProposalRepository;
import com.skep.quotation.proposal.QuotationProposalStatus;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 선정 통보 받은 공급사가 인원(운전수/오퍼레이터/작업자) 다중 선택 + 단가로 send.
 * 장비 배차(DispatchedEquipmentService)와 동일 패턴. 멱등: 같은 (request, supplier) 이미 send 했으면 409.
 */
@Service
@RequiredArgsConstructor
public class DispatchedPersonService {

    private final DispatchedPersonRepository dispatched;
    private final QuotationRequestRepository requests;
    private final QuotationProposalRepository proposals;
    private final PersonRepository persons;
    private final CompanyRepository companies;
    private final com.skep.company.CompanyService companyService;
    private final NotificationService notifications;
    private final com.skep.quotation.dispatch.draft.DispatchDraftRepository drafts;

    @Transactional
    public List<DispatchedPersonResponse> send(Long requestId, DispatchPersonRequest req, AuthenticatedUser actor) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_ITEMS", "보낼 인원을 1명 이상 선택하세요");
        }
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 가능합니다");
        }
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));

        Long supplierCompanyId = actor.role() == Role.ADMIN ? null : actor.companyId();
        if (supplierCompanyId == null && actor.role() == Role.ADMIN) {
            Long firstPersonId = req.items().get(0).personId();
            Person first = persons.findById(firstPersonId)
                    .orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND", "인원 없음"));
            supplierCompanyId = first.getSupplierId();
        }
        // 선정 확인 — 이 공급사가 FINAL_ACCEPTED 인지
        boolean selected = proposals.existsByRequestIdAndSupplierCompanyIdAndStatusIn(
                requestId, supplierCompanyId, List.of(QuotationProposalStatus.FINAL_ACCEPTED));
        if (!selected) {
            throw ApiException.badRequest("NOT_SELECTED", "선정된 공급사만 인원을 보낼 수 있습니다");
        }
        // 멱등 — 이 공급사가 같은 견적에 이미 인원 send 했으면 409
        if (dispatched.existsByQuotationRequestIdAndSupplierCompanyId(requestId, supplierCompanyId)) {
            throw ApiException.conflict("ALREADY_DISPATCHED", "이미 인원을 보낸 견적입니다");
        }

        // 인원 검증 — 본인 회사 소속만
        var personIds = req.items().stream().map(DispatchPersonRequest.Item::personId).distinct().toList();
        var personMap = persons.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, p -> p));
        final Long finalSupplier = supplierCompanyId;
        // V77 4-a: 본인 + 직속 자식 소유 인원까지 발송 허용(부모 명의). 자식/형제/타사는 selfAndChildren={본인} 이라 차단.
        java.util.List<Long> ownScope = companyService.selfAndChildren(finalSupplier);
        for (var item : req.items()) {
            Person p = personMap.get(item.personId());
            if (p == null) {
                throw ApiException.badRequest("PERSON_NOT_FOUND", "인원 #" + item.personId() + " 없음");
            }
            if (!ownScope.contains(p.getSupplierId())) {
                throw ApiException.forbidden("PERSON_NOT_OWNED", "본인/하위 공급사 인원만 보낼 수 있습니다");
            }
        }

        var entities = req.items().stream()
                .map(item -> DispatchedPerson.builder()
                        .quotationRequestId(requestId)
                        .supplierCompanyId(finalSupplier)
                        // 대외 명의는 부모(finalSupplier), 실소유가 자식이면 sub 에 자식 귀속.
                        .subSupplierCompanyId(subOwnerOrNull(personMap.get(item.personId()).getSupplierId(), finalSupplier))
                        .personId(item.personId())
                        .dailyPrice(item.dailyPrice())
                        .otDailyPrice(item.otDailyPrice())
                        .monthlyPrice(item.monthlyPrice())
                        .otMonthlyPrice(item.otMonthlyPrice())
                        .notes(item.notes() != null ? item.notes() : req.notes())
                        .sentBy(actor.id())
                        .build())
                .toList();
        dispatched.saveAll(entities);

        // BP 회사 알림
        Long bpCompanyId = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
        if (bpCompanyId != null) {
            String supplierName = companies.findById(finalSupplier).map(Company::getName).orElse("공급사");
            notifications.sendToCompany(bpCompanyId, "QUOTATION_DISPATCH", "인원 견적서 도착",
                    supplierName + " — 인원 " + entities.size() + "명 (견적 #" + requestId + ")",
                    "QUOTATION_REQUEST", requestId, qr.getSiteId());
        }

        // V80: 이 (요청,공급사)로 발송 완료 → 잔존 DRAFT 초안 폐기. confirm 경로면 이후 CONFIRMED 로 덮어씀.
        drafts.findByQuotationRequestIdAndSupplierCompanyIdAndStatus(
                        requestId, finalSupplier, com.skep.quotation.dispatch.draft.DispatchDraftStatus.DRAFT)
                .forEach(com.skep.quotation.dispatch.draft.DispatchDraft::markDiscarded);

        return entities.stream()
                .map(d -> toResponse(d, personMap.get(d.getPersonId())))
                .toList();
    }

    /** 견적별 dispatched 인원 list. ADMIN/BP/해당공급사 만 조회.
     *  공급사 호출 시 자기 회사 dispatched 만 응답. */
    @Transactional(readOnly = true)
    public List<DispatchedPersonResponse> listByRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        ensureCanView(qr, actor);

        boolean isSupplier = actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
        // V77: 공급사는 (본인+직속자식 명의) 행 + 자기 귀속(sub==본인) 행만.
        List<DispatchedPerson> list = (isSupplier && actor.companyId() != null)
                ? dispatched.findVisibleForSupplier(requestId, companyService.selfAndChildren(actor.companyId()), actor.companyId())
                : dispatched.findByQuotationRequestId(requestId);
        if (list.isEmpty()) return List.of();

        var personIds = list.stream().map(DispatchedPerson::getPersonId).distinct().toList();
        var personMap = persons.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, p -> p));
        var supplierIds = list.stream().map(DispatchedPerson::getSupplierCompanyId).distinct().toList();
        Map<Long, String> supplierNames = companies.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));

        return list.stream()
                .map(d -> {
                    Person p = personMap.get(d.getPersonId());
                    String label = p != null ? p.getName() : "#" + d.getPersonId();
                    String jobTitle = p != null ? p.getJobTitle() : null;
                    return DispatchedPersonResponse.from(d, supplierNames.get(d.getSupplierCompanyId()), label, jobTitle);
                })
                .toList();
    }

    /** V77: 자원 실소유가 발송자(부모)와 다르면(=자식) 자식 id, 같으면 null. */
    private static Long subOwnerOrNull(Long resourceSupplierId, Long senderCompanyId) {
        return resourceSupplierId != null && !resourceSupplierId.equals(senderCompanyId) ? resourceSupplierId : null;
    }

    private DispatchedPersonResponse toResponse(DispatchedPerson d, Person p) {
        String label = p != null ? p.getName() : "#" + d.getPersonId();
        String jobTitle = p != null ? p.getJobTitle() : null;
        String supplierName = companies.findById(d.getSupplierCompanyId()).map(Company::getName).orElse(null);
        return DispatchedPersonResponse.from(d, supplierName, label, jobTitle);
    }

    private void ensureCanView(QuotationRequest qr, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Long companyId = actor.companyId();
        if (companyId == null) throw ApiException.forbidden("NO_COMPANY", "회사 미지정");
        if (qr.getBpCompanyId() != null && qr.getBpCompanyId().equals(companyId)) return;
        boolean selected = proposals.existsByRequestIdAndSupplierCompanyIdAndStatusIn(
                qr.getId(), companyId, List.of(QuotationProposalStatus.FINAL_ACCEPTED));
        if (selected) return;
        // V77: 부모가 4-a 로 자기 명의 발송한 자원의 실소유 자식 — 자기 귀속분 열람 허용(읽기 전용).
        if (dispatched.existsByQuotationRequestIdAndSubSupplierCompanyId(qr.getId(), companyId)) return;
        throw ApiException.forbidden("NOT_PERMITTED", "조회 권한 없음");
    }
}
