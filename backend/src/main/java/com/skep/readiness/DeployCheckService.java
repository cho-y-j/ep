package com.skep.readiness;

import com.skep.common.ApiException;
import com.skep.compliance.ComplianceOrder;
import com.skep.compliance.ComplianceOrderService;
import com.skep.compliance.ComplianceTargetType;
import com.skep.document.OwnerType;
import com.skep.equipment.EquipmentService;
import com.skep.person.PersonService;
import com.skep.readiness.DeployCheckResponse.DeployBlock;
import com.skep.resourceCheck.ResourceCheckRequest;
import com.skep.resourceCheck.ResourceCheckRequestRepository;
import com.skep.resourceCheck.ResourceCheckService;
import com.skep.resourceCheck.ResourceCheckStatus;
import com.skep.resourceCheck.ResourceCheckType;
import com.skep.safety.InspectionStatus;
import com.skep.safety.InspectionTarget;
import com.skep.safety.SafetyInspectionRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.workplan.WorkPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * L3 교체/투입 사전판정 — 자원 1건이 (선택적으로 특정 현장에) 투입 가능한지 4게이트 합성.
 *
 * 기존 게이트 로직을 재구현하지 않고 재사용:
 *  - 서류: WorkPlanService.missingBlockingDocTypes (evaluateForClone/missingBlockingTypes 무수정 래퍼)
 *  - 이행지시: ComplianceOrderService.findBlockingFor (WorkPlanService.start Compl-5 와 동일 호출)
 *  - 안전점검: siteId 있으면 그 현장 COMPLETED(WorkPlanService.start G-1: WorkPlanService.java:819-823 와 동일 술어),
 *             없으면 자원 전체 anyMatch COMPLETED(ResourceReadinessService 미러).
 *  - 점검: 투입 게이트(§7 확정) — 장비=반입검사(VEHICLE_SAFETY) APPROVED, 인원=건강검진(HEALTH_CHECK)+안전교육(SAFETY_TRAINING) APPROVED.
 *
 * 접근권한: equipmentService.get / personService.get 의 기존 스코프(ADMIN·공급사 본인+자식·BP 사이트 참여)를 그대로 준수.
 */
@Service
@RequiredArgsConstructor
public class DeployCheckService {

    private final EquipmentService equipmentService;
    private final PersonService personService;
    private final WorkPlanService workPlanService;
    private final ResourceCheckRequestRepository resourceChecks;
    private final SafetyInspectionRepository safetyInspections;
    private final ComplianceOrderService complianceOrders;

    @Transactional(readOnly = true)
    public DeployCheckResponse check(String ownerTypeRaw, Long ownerId, Long siteId, AuthenticatedUser actor) {
        OwnerType ownerType = parseOwnerType(ownerTypeRaw);
        // 접근권한 + 존재 검증 — 기존 자원 스코프 그대로.
        if (ownerType == OwnerType.EQUIPMENT) {
            equipmentService.get(ownerId, actor);
        } else {
            personService.get(ownerId, actor);
        }

        List<DeployBlock> blocks = new ArrayList<>();

        // 1) 서류 — blocks_assignment 미검증/만료 종류.
        for (var t : workPlanService.missingBlockingDocTypes(ownerType, ownerId)) {
            blocks.add(new DeployBlock("DOCUMENT", "필수 서류 미비", t.getName() + " 서류를 등록하고 검증받으세요"));
        }

        // 2) 점검 — 투입 게이트 필수 종류가 APPROVED 인지.
        List<ResourceCheckRequest> checks = resourceChecks.findByOwnerTypeAndOwnerIdOrderByIdDesc(ownerType, ownerId);
        for (ResourceCheckType required : requiredCheckTypes(ownerType)) {
            boolean approved = checks.stream()
                    .anyMatch(c -> c.getCheckType() == required && c.getStatus() == ResourceCheckStatus.APPROVED);
            if (!approved) {
                blocks.add(new DeployBlock("CHECK",
                        ResourceCheckService.checkTypeLabel(required) + " 미완", checkActionText(required)));
            }
        }

        // 3) 안전점검 — siteId 있으면 그 현장 COMPLETED, 없으면 자원 전체 anyMatch COMPLETED.
        InspectionTarget target = ownerType == OwnerType.EQUIPMENT ? InspectionTarget.VEHICLE : InspectionTarget.PERSON;
        boolean safetyDone = (siteId != null
                ? safetyInspections.findBySiteIdAndTargetTypeAndTargetId(siteId, target, ownerId)
                : safetyInspections.findByTargetTypeAndTargetIdIn(target, List.of(ownerId)))
                .stream().anyMatch(s -> s.getStatus() == InspectionStatus.COMPLETED);
        if (!safetyDone) {
            blocks.add(new DeployBlock("SAFETY", "안전점검 미완",
                    siteId != null ? "이 현장 안전점검을 완료하세요" : "안전점검을 완료하세요"));
        }

        // 4) 이행지시 — 미해결 blocking.
        ComplianceTargetType complType = ownerType == OwnerType.EQUIPMENT
                ? ComplianceTargetType.VEHICLE : ComplianceTargetType.PERSON;
        List<ComplianceOrder> blocking = complianceOrders.findBlockingFor(complType, List.of(ownerId));
        if (!blocking.isEmpty()) {
            blocks.add(new DeployBlock("COMPLIANCE", "이행지시 " + blocking.size() + "건", "미해결 이행지시를 완료하세요"));
        }

        return new DeployCheckResponse(blocks.isEmpty(), blocks);
    }

    private static OwnerType parseOwnerType(String raw) {
        if (raw == null) throw ApiException.badRequest("INVALID_OWNER_TYPE", "ownerType 이 필요합니다");
        String v = raw.trim().toUpperCase();
        if (v.equals("EQUIPMENT") || v.equals("EQUIPMENTS")) return OwnerType.EQUIPMENT;
        if (v.equals("PERSON") || v.equals("PERSONS") || v.equals("PEOPLE")) return OwnerType.PERSON;
        throw ApiException.badRequest("INVALID_OWNER_TYPE", "지원하지 않는 자원 종류: " + raw);
    }

    private static List<ResourceCheckType> requiredCheckTypes(OwnerType ownerType) {
        return ownerType == OwnerType.EQUIPMENT
                ? List.of(ResourceCheckType.VEHICLE_SAFETY)
                : List.of(ResourceCheckType.HEALTH_CHECK, ResourceCheckType.SAFETY_TRAINING);
    }

    /** 부족 점검 종류별 행동 안내 문구. */
    private static String checkActionText(ResourceCheckType t) {
        return switch (t) {
            case VEHICLE_SAFETY -> "반입검사(차량 안전점검)를 완료하고 승인받으세요";
            case HEALTH_CHECK -> "건강검진을 완료하고 승인받으세요";
            case SAFETY_TRAINING -> "안전교육을 이수하고 승인받으세요";
            default -> ResourceCheckService.checkTypeLabel(t) + "을(를) 완료하고 승인받으세요";
        };
    }
}
