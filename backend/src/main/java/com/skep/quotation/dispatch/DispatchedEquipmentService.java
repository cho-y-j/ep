package com.skep.quotation.dispatch;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.quotation.QuotationMode;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.QuotationRequestTarget;
import com.skep.quotation.QuotationRequestTargetRepository;
import com.skep.quotation.dispatch.dto.DispatchRequest;
import com.skep.quotation.dispatch.dto.DispatchedEquipmentResponse;
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
 * 선정 통보 받은 공급사가 차량 다중 선택 + 단가로 send.
 * 멱등: 같은 (request, equipment) 가 이미 있으면 409. UNIQUE 제약과 service 사전 체크 이중 보장.
 */
@Service
@RequiredArgsConstructor
public class DispatchedEquipmentService {

    private final DispatchedEquipmentRepository dispatched;
    private final QuotationRequestRepository requests;
    private final QuotationRequestTargetRepository requestTargets;
    private final QuotationProposalRepository proposals;
    private final EquipmentRepository equipments;
    private final CompanyRepository companies;
    private final com.skep.company.CompanyService companyService;
    private final NotificationService notifications;
    private final com.skep.quotation.dispatch.draft.DispatchDraftRepository drafts;

    @Transactional
    public List<DispatchedEquipmentResponse> send(Long requestId, DispatchRequest req, AuthenticatedUser actor) {
        if (req == null) {
            throw ApiException.badRequest("EMPTY_RATES", "단가를 입력하세요");
        }
        boolean isRateMode = req.items() == null || req.items().isEmpty();
        if (isRateMode
                && req.dailyPrice() == null && req.otDailyPrice() == null
                && req.monthlyPrice() == null && req.otMonthlyPrice() == null) {
            throw ApiException.badRequest("EMPTY_RATES", "단가를 1개 이상 입력하세요");
        }
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "장비공급사만 가능합니다");
        }
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));

        Long supplierCompanyId = actor.role() == Role.ADMIN ? null : actor.companyId();
        if (supplierCompanyId == null && actor.role() == Role.ADMIN && !isRateMode) {
            // ADMIN 대행 + 차량지정 모드: items 의 첫 장비 supplier 사용.
            Long firstEqId = req.items().get(0).equipmentId();
            Equipment first = equipments.findById(firstEqId)
                    .orElseThrow(() -> ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비 없음"));
            supplierCompanyId = first.getSupplierId();
        }
        if (supplierCompanyId == null) {
            throw ApiException.badRequest("SUPPLIER_REQUIRED", "공급사 식별 불가");
        }
        // 선정 확인 — 이 공급사가 FINAL_ACCEPTED 인지
        boolean selected = proposals.existsByRequestIdAndSupplierCompanyIdAndStatusIn(
                requestId, supplierCompanyId, List.of(QuotationProposalStatus.FINAL_ACCEPTED));
        if (!selected) {
            throw ApiException.badRequest("NOT_SELECTED", "선정된 공급사만 보낼 수 있습니다");
        }
        // 멱등 — 이 공급사가 같은 견적에 이미 send 했으면 409
        if (dispatched.existsByQuotationRequestIdAndSupplierCompanyId(requestId, supplierCompanyId)) {
            throw ApiException.conflict("ALREADY_DISPATCHED", "이미 응답한 견적입니다");
        }

        final Long finalSupplier = supplierCompanyId;
        List<DispatchedEquipment> entities;
        Map<Long, Equipment> eqMap;

        if (isRateMode) {
            // 단가 모드 — 차량 미지정 1행
            eqMap = Map.of();
            entities = List.of(DispatchedEquipment.builder()
                    .quotationRequestId(requestId)
                    .supplierCompanyId(finalSupplier)
                    .equipmentId(null)
                    .dailyPrice(req.dailyPrice())
                    .otDailyPrice(req.otDailyPrice())
                    .monthlyPrice(req.monthlyPrice())
                    .otMonthlyPrice(req.otMonthlyPrice())
                    .notes(req.notes())
                    .dailyNote(req.dailyNote())
                    .otDailyNote(req.otDailyNote())
                    .monthlyNote(req.monthlyNote())
                    .otMonthlyNote(req.otMonthlyNote())
                    .sentBy(actor.id())
                    .build());
        } else {
            // 차량 지정 모드 — items 마다 1행
            var eqIds = req.items().stream().map(DispatchRequest.Item::equipmentId).distinct().toList();
            eqMap = equipments.findAllById(eqIds).stream()
                    .collect(Collectors.toMap(Equipment::getId, e -> e));
            // V77 4-a: 본인 + 직속 자식 소유 장비까지 발송 허용(부모 명의). 자식/형제/타사는 selfAndChildren={본인} 이라 차단.
            java.util.List<Long> ownScope = companyService.selfAndChildren(finalSupplier);
            for (var item : req.items()) {
                Equipment e = eqMap.get(item.equipmentId());
                if (e == null) throw ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비 #" + item.equipmentId() + " 없음");
                if (!ownScope.contains(e.getSupplierId())) {
                    throw ApiException.forbidden("EQUIPMENT_NOT_OWNED", "본인/하위 공급사 장비만 보낼 수 있습니다");
                }
            }
            final Map<Long, Equipment> itemEqMap = eqMap;
            entities = req.items().stream()
                    .map(item -> DispatchedEquipment.builder()
                            .quotationRequestId(requestId)
                            .supplierCompanyId(finalSupplier)
                            // 대외 명의는 부모(finalSupplier), 실소유가 자식이면 sub 에 자식 귀속.
                            .subSupplierCompanyId(subOwnerOrNull(itemEqMap.get(item.equipmentId()).getSupplierId(), finalSupplier))
                            .equipmentId(item.equipmentId())
                            .dailyPrice(item.dailyPrice())
                            .otDailyPrice(item.otDailyPrice())
                            .monthlyPrice(item.monthlyPrice())
                            .otMonthlyPrice(item.otMonthlyPrice())
                            .notes(item.notes() != null ? item.notes() : req.notes())
                            .dailyNote(item.dailyNote())
                            .otDailyNote(item.otDailyNote())
                            .monthlyNote(item.monthlyNote())
                            .otMonthlyNote(item.otMonthlyNote())
                            .sentBy(actor.id())
                            .build())
                    .toList();
        }
        dispatched.saveAll(entities);

        // BP 회사 알림
        Long bpCompanyId = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
        if (bpCompanyId != null) {
            String supplierName = companies.findById(finalSupplier).map(Company::getName).orElse("공급사");
            String title = "견적서 도착";
            String message = supplierName + (isRateMode ? " — 단가 응답" : " — 차량 " + entities.size() + "대") + " (견적 #" + requestId + ")";
            notifications.sendToCompany(bpCompanyId, "QUOTATION_DISPATCH", title, message,
                    "QUOTATION_REQUEST", requestId, qr.getSiteId());
        }

        // V80: 이 (요청,공급사)로 발송 완료 → 잔존 DRAFT 초안 폐기. confirm 경로면 이후 CONFIRMED 로 덮어씀.
        drafts.findByQuotationRequestIdAndSupplierCompanyIdAndStatus(
                        requestId, finalSupplier, com.skep.quotation.dispatch.draft.DispatchDraftStatus.DRAFT)
                .forEach(com.skep.quotation.dispatch.draft.DispatchDraft::markDiscarded);

        final Map<Long, Equipment> finalEqMap = eqMap;
        return entities.stream()
                .map(d -> toResponse(d, d.getEquipmentId() != null ? finalEqMap.get(d.getEquipmentId()) : null, qr.getBpCompanyId()))
                .toList();
    }

    /** 견적별 dispatched 차량 list. ADMIN/BP/해당공급사 만 조회 가능.
     *  공급사 호출 시 자기 회사 dispatched 만 응답 (멀티 공급사 견적의 경우 경쟁사 가격 노출 방지). */
    @Transactional(readOnly = true)
    public List<DispatchedEquipmentResponse> listByRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        ensureCanView(qr, actor);

        boolean isSupplier = actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
        // V77: 공급사는 (본인+직속자식 명의) 행 + 자기 귀속(sub==본인) 행만. 부모↔자식 각자 필요한 만큼만 노출.
        List<DispatchedEquipment> list = (isSupplier && actor.companyId() != null)
                ? dispatched.findVisibleForSupplier(requestId, companyService.selfAndChildren(actor.companyId()), actor.companyId())
                : dispatched.findByQuotationRequestId(requestId);
        if (list.isEmpty()) return List.of();

        var eqIds = list.stream().map(DispatchedEquipment::getEquipmentId).filter(java.util.Objects::nonNull).distinct().toList();
        var eqMap = equipments.findAllById(eqIds).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        var supplierIds = list.stream().map(DispatchedEquipment::getSupplierCompanyId).distinct().toList();
        Map<Long, String> supplierNames = companies.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));

        return list.stream()
                .map(d -> {
                    Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
                    String label = e != null
                            ? (e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "#" + e.getId()))
                            : "단가 응답";
                    String category = e != null && e.getCategory() != null ? e.getCategory().name() : null;
                    return DispatchedEquipmentResponse.from(d, supplierNames.get(d.getSupplierCompanyId()), label, category);
                })
                .toList();
    }

    /** V77: 자원 실소유가 발송자(부모)와 다르면(=자식) 자식 id, 같으면 null. */
    private static Long subOwnerOrNull(Long resourceSupplierId, Long senderCompanyId) {
        return resourceSupplierId != null && !resourceSupplierId.equals(senderCompanyId) ? resourceSupplierId : null;
    }

    private DispatchedEquipmentResponse toResponse(DispatchedEquipment d, Equipment e, Long bpCompanyId) {
        String label = e != null
                ? (e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "#" + e.getId()))
                : "단가 응답";
        String category = e != null && e.getCategory() != null ? e.getCategory().name() : null;
        String supplierName = companies.findById(d.getSupplierCompanyId()).map(Company::getName).orElse(null);
        return DispatchedEquipmentResponse.from(d, supplierName, label, category);
    }

    /** PDF endpoint 등 외부 호출자가 단건 권한만 체크하고 싶을 때. */
    @Transactional(readOnly = true)
    public void ensureCanReadRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        ensureCanView(qr, actor);
    }

    /**
     * 발송 전 미리보기 권한 — 선정(FINAL_ACCEPTED) 전 단계에서도 호출되므로 ensureCanView 보다 넓다.
     * 허용: ADMIN / 요청 소유 BP / OPEN_BID(공개 게시판이라 임의 공급사) / TARGETED 의 지정 대상 공급사.
     */
    public void ensureCanPreviewRequest(Long requestId, AuthenticatedUser actor) {
        QuotationRequest qr = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "견적 없음"));
        if (actor.role() == Role.ADMIN) return;
        Long companyId = actor.companyId();
        if (companyId == null) throw ApiException.forbidden("NO_COMPANY", "회사 미지정");
        if (qr.getBpCompanyId() != null && qr.getBpCompanyId().equals(companyId)) return;
        if (qr.getMode() == QuotationMode.OPEN_BID) return;
        boolean targeted = requestTargets.findByRequestIdOrderByIdAsc(requestId).stream()
                .map(QuotationRequestTarget::getSupplierCompanyId)
                .anyMatch(companyId::equals);
        if (!targeted) {
            throw ApiException.forbidden("NOT_PERMITTED", "미리보기 권한 없음");
        }
    }

    private void ensureCanView(QuotationRequest qr, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Long companyId = actor.companyId();
        if (companyId == null) throw ApiException.forbidden("NO_COMPANY", "회사 미지정");
        if (qr.getBpCompanyId() != null && qr.getBpCompanyId().equals(companyId)) return;
        // 본인이 선정된 공급사인 경우
        boolean selected = proposals.existsByRequestIdAndSupplierCompanyIdAndStatusIn(
                qr.getId(), companyId, List.of(QuotationProposalStatus.FINAL_ACCEPTED));
        if (selected) return;
        // V77: 부모가 4-a 로 자기 명의 발송한 자원의 실소유 자식 — 자기 귀속분 열람 허용(읽기 전용).
        if (dispatched.existsByQuotationRequestIdAndSubSupplierCompanyId(qr.getId(), companyId)) return;
        throw ApiException.forbidden("NOT_PERMITTED", "조회 권한 없음");
    }
}
