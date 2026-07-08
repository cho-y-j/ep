package com.skep.quotation.dispatch;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
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
    private final QuotationProposalRepository proposals;
    private final EquipmentRepository equipments;
    private final CompanyRepository companies;
    private final NotificationService notifications;

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
            for (var item : req.items()) {
                Equipment e = eqMap.get(item.equipmentId());
                if (e == null) throw ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비 #" + item.equipmentId() + " 없음");
                if (!e.getSupplierId().equals(supplierCompanyId)) {
                    throw ApiException.forbidden("EQUIPMENT_NOT_OWNED", "본인 회사 장비만 보낼 수 있습니다");
                }
            }
            entities = req.items().stream()
                    .map(item -> DispatchedEquipment.builder()
                            .quotationRequestId(requestId)
                            .supplierCompanyId(finalSupplier)
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
        List<DispatchedEquipment> list = (isSupplier && actor.companyId() != null)
                ? dispatched.findByQuotationRequestIdAndSupplierCompanyId(requestId, actor.companyId())
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

    private void ensureCanView(QuotationRequest qr, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Long companyId = actor.companyId();
        if (companyId == null) throw ApiException.forbidden("NO_COMPANY", "회사 미지정");
        if (qr.getBpCompanyId() != null && qr.getBpCompanyId().equals(companyId)) return;
        // 본인이 선정된 공급사인 경우
        boolean selected = proposals.existsByRequestIdAndSupplierCompanyIdAndStatusIn(
                qr.getId(), companyId, List.of(QuotationProposalStatus.FINAL_ACCEPTED));
        if (!selected) {
            throw ApiException.forbidden("NOT_PERMITTED", "조회 권한 없음");
        }
    }
}
