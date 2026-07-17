package com.skep.quotation.dispatch;

import com.skep.common.ApiException;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.dispatch.dto.DispatchedEquipmentResponse;
import com.skep.quotation.dispatch.dto.DispatchedPersonResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** BP 가 "내가 받은" dispatched 자원만 보는 화면 (투입 장비 / 투입 인원). */
@RestController
@RequestMapping("/api/bp-dispatched")
@RequiredArgsConstructor
public class BpDispatchedController {

    private final DispatchedEquipmentRepository dispatchedEq;
    private final DispatchedPersonRepository dispatchedPp;
    private final QuotationRequestRepository qrRepo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companies;

    @GetMapping("/equipment")
    public List<DispatchedEquipmentResponse> bpEquipment(@AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 조회 가능");
        }
        Long bpId = actor.companyId();
        if (bpId == null) return List.of();
        var qrs = qrRepo.findByBpCompanyIdOrOnBehalfOfBpCompanyIdOrderByIdDesc(bpId, bpId);
        Set<Long> qrIds = qrs.stream().map(QuotationRequest::getId).collect(Collectors.toSet());
        if (qrIds.isEmpty()) return List.of();
        var rows = dispatchedEq.findByQuotationRequestIdInOrderBySentAtDesc(qrIds);

        Set<Long> eqIds = rows.stream().map(DispatchedEquipment::getEquipmentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> supplierIds = rows.stream().map(DispatchedEquipment::getSupplierCompanyId).collect(Collectors.toSet());
        Map<Long, Equipment> eqMap = equipmentRepo.findAllById(eqIds).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<Long, String> supplierMap = new HashMap<>();
        companies.findAllById(supplierIds).forEach(c -> supplierMap.put(c.getId(), c.getName()));

        return rows.stream().map(d -> {
            Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
            String label = e != null ? (e.getVehicleNo() != null ? e.getVehicleNo() : e.getModel()) : ("#" + d.getEquipmentId());
            String category = e != null ? e.getCategory() : null;
            return DispatchedEquipmentResponse.from(d, supplierMap.get(d.getSupplierCompanyId()), label, category);
        }).toList();
    }

    /** 공급사가 BP에게 보낸(견적 응답한) 자기 회사 dispatched 자원. 현장 투입 요청 선택용. */
    @GetMapping("/supplier-equipment")
    public List<Map<String, Object>> supplierEquipment(@AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("DENIED", "공급사 전용");
        }
        if (actor.companyId() == null) return List.of();
        var rows = dispatchedEq.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        Set<Long> eqIds = rows.stream().map(DispatchedEquipment::getEquipmentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> qrIds = rows.stream().map(DispatchedEquipment::getQuotationRequestId).collect(Collectors.toSet());
        Map<Long, Equipment> eqMap = equipmentRepo.findAllById(eqIds).stream().collect(Collectors.toMap(Equipment::getId, e -> e));
        Map<Long, Long> qrToBp = new HashMap<>();
        qrRepo.findAllById(qrIds).forEach(q -> {
            Long bp = q.getBpCompanyId() != null ? q.getBpCompanyId() : q.getOnBehalfOfBpCompanyId();
            if (bp != null) qrToBp.put(q.getId(), bp);
        });
        Set<Long> bpIds = new java.util.HashSet<>(qrToBp.values());
        Map<Long, String> bpNames = new HashMap<>();
        companies.findAllById(bpIds).forEach(c -> bpNames.put(c.getId(), c.getName()));
        return rows.stream().map(d -> {
            Equipment e = d.getEquipmentId() != null ? eqMap.get(d.getEquipmentId()) : null;
            String label = e != null ? (e.getVehicleNo() != null ? e.getVehicleNo() : e.getModel()) : ("#" + d.getEquipmentId());
            Long bpId = qrToBp.get(d.getQuotationRequestId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("equipment_id", d.getEquipmentId());
            m.put("equipment_label", label);
            m.put("bp_company_id", bpId);
            m.put("bp_company_name", bpId != null ? bpNames.get(bpId) : null);
            m.put("quotation_request_id", d.getQuotationRequestId());
            m.put("daily_price", d.getDailyPrice());
            m.put("ot_daily_price", d.getOtDailyPrice());
            m.put("monthly_price", d.getMonthlyPrice());
            m.put("ot_monthly_price", d.getOtMonthlyPrice());
            m.put("sent_at", d.getSentAt());
            return m;
        }).toList();
    }

    @GetMapping("/supplier-persons")
    public List<Map<String, Object>> supplierPersons(@AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor.role() != Role.MANPOWER_SUPPLIER && actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("DENIED", "공급사 전용");
        }
        if (actor.companyId() == null) return List.of();
        var rows = dispatchedPp.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        Set<Long> personIds = rows.stream().map(DispatchedPerson::getPersonId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> qrIds = rows.stream().map(DispatchedPerson::getQuotationRequestId).collect(Collectors.toSet());
        Map<Long, Person> personMap = personRepo.findAllById(personIds).stream().collect(Collectors.toMap(Person::getId, p -> p));
        Map<Long, Long> qrToBp = new HashMap<>();
        qrRepo.findAllById(qrIds).forEach(q -> {
            Long bp = q.getBpCompanyId() != null ? q.getBpCompanyId() : q.getOnBehalfOfBpCompanyId();
            if (bp != null) qrToBp.put(q.getId(), bp);
        });
        Set<Long> bpIds = new java.util.HashSet<>(qrToBp.values());
        Map<Long, String> bpNames = new HashMap<>();
        companies.findAllById(bpIds).forEach(c -> bpNames.put(c.getId(), c.getName()));
        return rows.stream().map(d -> {
            Person p = d.getPersonId() != null ? personMap.get(d.getPersonId()) : null;
            String label = p != null ? p.getName() : ("#" + d.getPersonId());
            Long bpId = qrToBp.get(d.getQuotationRequestId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.getId());
            m.put("person_id", d.getPersonId());
            m.put("person_label", label);
            m.put("bp_company_id", bpId);
            m.put("bp_company_name", bpId != null ? bpNames.get(bpId) : null);
            m.put("quotation_request_id", d.getQuotationRequestId());
            m.put("daily_price", d.getDailyPrice());
            m.put("monthly_price", d.getMonthlyPrice());
            m.put("sent_at", d.getSentAt());
            return m;
        }).toList();
    }

    @GetMapping("/persons")
    public List<DispatchedPersonResponse> bpPersons(@AuthenticationPrincipal AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 조회 가능");
        }
        Long bpId = actor.companyId();
        if (bpId == null) return List.of();
        var qrs = qrRepo.findByBpCompanyIdOrOnBehalfOfBpCompanyIdOrderByIdDesc(bpId, bpId);
        Set<Long> qrIds = qrs.stream().map(QuotationRequest::getId).collect(Collectors.toSet());
        if (qrIds.isEmpty()) return List.of();
        var rows = dispatchedPp.findByQuotationRequestIdInOrderByIdDesc(qrIds);

        Set<Long> personIds = rows.stream().map(DispatchedPerson::getPersonId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> supplierIds = rows.stream().map(DispatchedPerson::getSupplierCompanyId).collect(Collectors.toSet());
        Map<Long, Person> personMap = personRepo.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, p -> p));
        Map<Long, String> supplierMap = new HashMap<>();
        companies.findAllById(supplierIds).forEach(c -> supplierMap.put(c.getId(), c.getName()));

        return rows.stream().map(d -> {
            Person p = d.getPersonId() != null ? personMap.get(d.getPersonId()) : null;
            String label = p != null ? p.getName() : ("#" + d.getPersonId());
            String job = p != null && p.getRoles() != null && !p.getRoles().isEmpty()
                    ? p.getRoles().iterator().next().name() : null;
            return DispatchedPersonResponse.from(d, supplierMap.get(d.getSupplierCompanyId()), label, job);
        }).toList();
    }
}
