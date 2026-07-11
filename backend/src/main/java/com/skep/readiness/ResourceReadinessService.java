package com.skep.readiness;

import com.skep.company.CompanyService;
import com.skep.compliance.ComplianceOrder;
import com.skep.compliance.ComplianceOrderService;
import com.skep.compliance.ComplianceTargetType;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.resourceCheck.ResourceCheckRequest;
import com.skep.resourceCheck.ResourceCheckRequestRepository;
import com.skep.resourceCheck.ResourceCheckService;
import com.skep.resourceCheck.ResourceCheckStatus;
import com.skep.safety.InspectionStatus;
import com.skep.safety.InspectionTarget;
import com.skep.safety.SafetyInspection;
import com.skep.safety.SafetyInspectionRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 투입 대기 가시성 — 작업계획서 게이트와 동일 판정을 읽기전용으로 노출. 저장/마이그레이션 없음.
 * 게이트를 재구현하지 않고 동일 상태 술어를 미러링한다:
 *  - 점검 전부 APPROVED   ← WorkPlanService.submit RESOURCE_CHECKS_INCOMPLETE (WorkPlanService.java:730-741)
 *  - 안전점검 COMPLETED   ← WorkPlanService.start G-1 SAFETY_INSPECTION_INCOMPLETE (WorkPlanService.java:797-808)
 *  - 미해결 이행지시 없음  ← WorkPlanService.start Compl-5 COMPLIANCE_ORDER_PENDING (WorkPlanService.java:822-831)
 *                           (ComplianceOrderService.findBlockingFor 를 그대로 재사용 — 완전 일치)
 *
 * 스코프 차이(정직): 게이트는 작업계획서/현장 컨텍스트, 본 가시성은 자원 컨텍스트다.
 *  - 점검: 게이트는 workPlanId 로 조회, 여기서는 자원(ownerType,ownerId) 로 조회. 술어(APPROVED/CANCELLED 통과)는 동일.
 *  - 안전점검: 게이트는 siteId 한정, 여기서는 자원 전체(현장 무관) anyMatch(COMPLETED). 술어는 동일.
 *  - 이행지시: findBlockingFor(type, ids) 동일 호출/동일 쿼리.
 */
@Service
@RequiredArgsConstructor
public class ResourceReadinessService {

    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final ResourceCheckRequestRepository resourceChecks;
    private final SafetyInspectionRepository safetyInspections;
    private final ComplianceOrderService complianceOrders;
    private final CompanyService companyService;

    /** 공급사/협력사 본인 + 직속 자식 소유 자원의 투입 준비 상태(장비·인원). 그 외 역할/회사미상은 빈 목록. */
    @Transactional(readOnly = true)
    public List<ResourceReadinessResponse> listForActor(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            return List.of();
        }
        if (actor.companyId() == null) return List.of();

        // 스코프 = 본인 + 직속 자식(협력사) — V77 selfAndChildren(부모→자식 단방향; 자식이면 {본인}).
        return listForScope(companyService.selfAndChildren(actor.companyId()));
    }

    /**
     * 주어진 회사 스코프가 소유한 자원(장비·인원)의 투입 준비 상태. 스코프 격리는 호출자 책임.
     * (B4 자식별 롤업이 자식 1건 스코프로 재사용.)
     */
    @Transactional(readOnly = true)
    public List<ResourceReadinessResponse> listForScope(List<Long> scope) {
        if (scope == null || scope.isEmpty()) return List.of();
        List<Equipment> equipment = equipmentRepo.findBySupplierIdInOrderByIdDesc(scope);
        List<Person> persons = personRepo.findBySupplierIdInOrderByIdDesc(scope);

        List<Long> equipIds = equipment.stream().map(Equipment::getId).toList();
        List<Long> personIds = persons.stream().map(Person::getId).toList();

        List<ResourceReadinessResponse> out = new ArrayList<>();

        // 장비 — OwnerType.EQUIPMENT / InspectionTarget.VEHICLE / ComplianceTargetType.VEHICLE
        Map<Long, List<ResourceCheckRequest>> eqChecks = groupChecks(OwnerType.EQUIPMENT, equipIds);
        Map<Long, List<SafetyInspection>> eqInsp = groupInspections(InspectionTarget.VEHICLE, equipIds);
        Map<Long, Long> eqBlocking = groupBlocking(ComplianceTargetType.VEHICLE, equipIds);
        for (Equipment e : equipment) {
            out.add(evaluate("EQUIPMENT", e.getId(), equipmentLabel(e),
                    eqChecks.getOrDefault(e.getId(), List.of()),
                    eqInsp.getOrDefault(e.getId(), List.of()),
                    eqBlocking.getOrDefault(e.getId(), 0L)));
        }

        // 인원 — OwnerType.PERSON / InspectionTarget.PERSON / ComplianceTargetType.PERSON
        Map<Long, List<ResourceCheckRequest>> pChecks = groupChecks(OwnerType.PERSON, personIds);
        Map<Long, List<SafetyInspection>> pInsp = groupInspections(InspectionTarget.PERSON, personIds);
        Map<Long, Long> pBlocking = groupBlocking(ComplianceTargetType.PERSON, personIds);
        for (Person p : persons) {
            out.add(evaluate("PERSON", p.getId(),
                    p.getName() != null ? p.getName() : "인원 #" + p.getId(),
                    pChecks.getOrDefault(p.getId(), List.of()),
                    pInsp.getOrDefault(p.getId(), List.of()),
                    pBlocking.getOrDefault(p.getId(), 0L)));
        }
        return out;
    }

    /** 게이트 3종 술어를 그대로 적용해 자원 1건의 ready + pending 산출. */
    private ResourceReadinessResponse evaluate(String resourceType, Long id, String label,
                                               List<ResourceCheckRequest> checks,
                                               List<SafetyInspection> inspections,
                                               long blockingCount) {
        List<String> pending = new ArrayList<>();

        // 1) 점검 전부 APPROVED — WorkPlanService.java:731-732 술어 그대로(APPROVED/CANCELLED 만 통과).
        //    점검 요청 자체가 없으면 통과(게이트: wpChecks 비면 notApproved 비어 통과).
        List<String> checkPending = checks.stream()
                .filter(c -> c.getStatus() != ResourceCheckStatus.APPROVED
                        && c.getStatus() != ResourceCheckStatus.CANCELLED)
                .map(c -> ResourceCheckService.checkTypeLabel(c.getCheckType()) + " 미승인")
                .distinct().toList();
        pending.addAll(checkPending);

        // 2) 안전점검 COMPLETED — WorkPlanService.java:800 술어 그대로.
        boolean safetyDone = inspections.stream()
                .anyMatch(s -> s.getStatus() == InspectionStatus.COMPLETED);
        if (!safetyDone) pending.add("안전점검 미완");

        // 3) 미해결 이행지시 없음 — WorkPlanService.java:822-831 (findBlockingFor 결과 존재 시 차단).
        if (blockingCount > 0) pending.add("이행지시 " + blockingCount + "건");

        return new ResourceReadinessResponse(resourceType, id, label, pending.isEmpty(), pending);
    }

    private Map<Long, List<ResourceCheckRequest>> groupChecks(OwnerType type, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return resourceChecks.findByOwnerTypeAndOwnerIdIn(type, ids).stream()
                .collect(Collectors.groupingBy(ResourceCheckRequest::getOwnerId));
    }

    private Map<Long, List<SafetyInspection>> groupInspections(InspectionTarget target, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return safetyInspections.findByTargetTypeAndTargetIdIn(target, ids).stream()
                .collect(Collectors.groupingBy(SafetyInspection::getTargetId));
    }

    private Map<Long, Long> groupBlocking(ComplianceTargetType target, List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return complianceOrders.findBlockingFor(target, ids).stream()
                .collect(Collectors.groupingBy(ComplianceOrder::getTargetId, Collectors.counting()));
    }

    /** ResourceCheckService.ownerLabel(장비) 미러: model → vehicleNo → "장비 #id". */
    private String equipmentLabel(Equipment e) {
        if (e.getModel() != null) return e.getModel();
        if (e.getVehicleNo() != null) return e.getVehicleNo();
        return "장비 #" + e.getId();
    }
}
