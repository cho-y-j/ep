package com.skep.readiness;

import com.skep.common.ApiException;
import com.skep.compliance.ComplianceOrder;
import com.skep.compliance.ComplianceOrderService;
import com.skep.compliance.ComplianceTargetType;
import com.skep.document.DocumentReviewItemRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.equipment.EquipmentService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
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
import com.skep.user.Role;
import com.skep.workplan.WorkPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final EquipmentRepository equipmentRepo;
    private final PersonService personService;
    private final PersonRepository personRepo;
    private final WorkPlanService workPlanService;
    private final ResourceCheckRequestRepository resourceChecks;
    private final SafetyInspectionRepository safetyInspections;
    private final ComplianceOrderService complianceOrders;
    private final DocumentReviewItemRepository reviewItemRepo;

    @Transactional(readOnly = true)
    public DeployCheckResponse check(String ownerTypeRaw, Long ownerId, Long siteId, AuthenticatedUser actor) {
        OwnerType ownerType = parseOwnerType(ownerTypeRaw);
        // 접근권한 + 존재 검증 — 기존 자원 스코프 그대로.
        // V96 미러(DocumentService.ensureCanAccess): 사이트 참여 전이라도 BP 앞 심사 봉투에 담긴 자원은
        // 서류 열람이 허용됨 — 같은 근거로 파생 판정(deploy-check)도 허용. 그 외(크로스테넌트·404)는 기존 그대로.
        // (get() 의 403 을 잡아 우회하면 조인된 트랜잭션이 rollback-only 로 오염되므로 grant 를 먼저 판정한다.)
        boolean bpReviewGrant = actor.role() == Role.BP && actor.companyId() != null
                && reviewItemRepo.existsForBpAndOwner(actor.companyId(), ownerType, ownerId);
        if (bpReviewGrant) {
            boolean exists = ownerType == OwnerType.EQUIPMENT
                    ? equipmentRepo.existsById(ownerId) : personRepo.existsById(ownerId);
            if (!exists) throw ApiException.notFound("RESOURCE_NOT_FOUND", "대상 자원 없음");
        } else if (ownerType == OwnerType.EQUIPMENT) {
            equipmentService.get(ownerId, actor);
        } else {
            personService.get(ownerId, actor);
        }
        List<DeployBlock> blocks = computeBlocks(ownerType, ownerId, siteId);
        return new DeployCheckResponse(blocks.isEmpty(), blocks);
    }

    /**
     * R1 조합(차량+조종원) 판정 — 4게이트를 장비 1회 + 조합(교대조) 조종원 N회 산출해 합성. 저장 없음.
     * combo_ready = 장비 ready AND 조종원 전원 ready (조종원 0명이면 장비 단독 판정).
     * 접근권한은 장비 접근권만 검사 — 조종원은 크로스회사 매칭이 가능해(PersonService.get 스코프 밖)
     * 내부(레포) 조회로 판정·이름만 노출한다(DocumentBundlePdfService 조종원 이름 배치 로드와 동일 선례).
     */
    @Transactional(readOnly = true)
    public ComboDeployCheckResponse checkCombo(Long equipmentId, Long siteId, AuthenticatedUser actor) {
        Equipment e = equipmentService.get(equipmentId, actor); // 접근권한 + 존재 검증 — 장비 스코프 그대로.
        // 조종원 목록 — priority 오름차순, 없으면 operator_person_id 단일 폴백(기존 배치 API 재사용).
        List<Long> operatorIds = equipmentService.defaultOperatorsByEquipmentIds(List.of(equipmentId), actor)
                .getOrDefault(equipmentId, List.of());

        List<DeployBlock> equipmentBlocks = computeBlocks(OwnerType.EQUIPMENT, equipmentId, siteId);
        DeployCheckResponse equipment = new DeployCheckResponse(equipmentBlocks.isEmpty(), equipmentBlocks);

        Map<Long, String> names = personRepo.findAllById(operatorIds).stream()
                .collect(Collectors.toMap(Person::getId, p -> p.getName() != null ? p.getName() : ("인원 #" + p.getId())));
        List<ComboDeployCheckResponse.OperatorCheck> operators = new ArrayList<>();
        for (int i = 0; i < operatorIds.size(); i++) {
            Long pid = operatorIds.get(i);
            List<DeployBlock> opBlocks = computeBlocks(OwnerType.PERSON, pid, siteId);
            operators.add(new ComboDeployCheckResponse.OperatorCheck(
                    pid, names.getOrDefault(pid, "인원 #" + pid), i + 1, opBlocks.isEmpty(), opBlocks));
        }
        boolean comboReady = equipment.ready()
                && operators.stream().allMatch(ComboDeployCheckResponse.OperatorCheck::ready);
        return new ComboDeployCheckResponse(equipmentId, equipmentLabel(e), comboReady, equipment, operators);
    }

    /** 4게이트 산출(접근검증 없음) — check(단건)와 checkCombo(조합)가 공유. 게이트 술어는 기존 그대로. */
    private List<DeployBlock> computeBlocks(OwnerType ownerType, Long ownerId, Long siteId) {
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

        return blocks;
    }

    /** ResourceCheckService.ownerLabel(장비)/ResourceReadinessService 미러: model → vehicleNo → "장비 #id". */
    private static String equipmentLabel(Equipment e) {
        if (e.getModel() != null) return e.getModel();
        if (e.getVehicleNo() != null) return e.getVehicleNo();
        return "장비 #" + e.getId();
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
            case VEHICLE_SAFETY -> "자동차 반입검사를 완료하고 승인받으세요";
            case HEALTH_CHECK -> "건강검진을 완료하고 승인받으세요";
            case SAFETY_TRAINING -> "안전교육을 이수하고 승인받으세요";
            default -> ResourceCheckService.checkTypeLabel(t) + "을(를) 완료하고 승인받으세요";
        };
    }
}
