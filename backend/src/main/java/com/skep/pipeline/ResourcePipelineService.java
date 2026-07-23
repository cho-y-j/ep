package com.skep.pipeline;

import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.compliance.ComplianceService;
import com.skep.compliance.dto.ResourceCompliance;
import com.skep.document.DocumentReview;
import com.skep.document.DocumentReviewItem;
import com.skep.document.DocumentReviewItemRepository;
import com.skep.document.DocumentReviewRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.fieldDeployment.FieldDeploymentRepository;
import com.skep.fieldDeployment.FieldDeploymentRequest;
import com.skep.fieldDeployment.FieldDeploymentStatus;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.pipeline.ResourcePipelineResponse.Stage;
import com.skep.pipeline.ResourcePipelineResponse.Stages;
import com.skep.quotation.dispatch.DispatchedEquipment;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.quotation.dispatch.DispatchedPerson;
import com.skep.quotation.dispatch.DispatchedPersonRepository;
import com.skep.readiness.ResourceReadinessResponse;
import com.skep.readiness.ResourceReadinessService;
import com.skep.resourceCheck.ResourceCheckRequest;
import com.skep.resourceCheck.ResourceCheckRequestRepository;
import com.skep.resourceCheck.ResourceCheckStatus;
import com.skep.safety.InspectionStatus;
import com.skep.safety.InspectionTarget;
import com.skep.safety.SafetyInspection;
import com.skep.safety.SafetyInspectionRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.workconfirmation.WorkConfirmation;
import com.skep.workconfirmation.WorkConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 자원 파이프라인 집계(읽기전용) — 기존 도메인 서비스/레포의 상태를 자원 1건 단위로 6단계로 모아 보여준다.
 * 도메인 로직/게이트를 재구현하지 않고 기존 산출물을 그대로 조합한다. 상태 저장/마이그레이션 없음.
 *
 * 단계별 근거(재사용):
 *  1) 서류      — ComplianceService.forEquipment/forPerson → ResourceCompliance(readyForWorkPlan/missing/rejected/expiring)
 *  2) 검사      — ResourceCheckRequestRepository.findByOwnerTypeAndOwnerIdIn + SafetyInspectionRepository.findByTargetTypeAndTargetIdIn (readiness 와 동일 배치)
 *  3) 투입대기  — ResourceReadinessService.listForActor (그대로 호출, 재구현 없음)
 *  4) 투입      — Dispatched{Equipment,Person}Repository.findAllVisibleForSupplier(정산 격리) + FieldDeployment ACTIVE
 *  5) 작업      — WorkConfirmationRepository (인력 최근 workDate; 장비는 해당없음)
 *  6) 정산      — 배차행 존재 시 "정산대상"(전체 정산 계산 재현하지 않음)
 *
 * 스코프/격리 = CompanyService.selfAndChildren(본인+직속 자식) — ResourceReadinessService 와 동일.
 */
@Service
@RequiredArgsConstructor
public class ResourcePipelineService {

    private static final String DONE = "DONE";
    private static final String PENDING = "PENDING";
    private static final String NA = "NA";

    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final ComplianceService complianceService;
    private final ResourceCheckRequestRepository resourceChecks;
    private final SafetyInspectionRepository safetyInspections;
    private final ResourceReadinessService readinessService;
    private final DispatchedEquipmentRepository dispatchedEquipment;
    private final DispatchedPersonRepository dispatchedPerson;
    private final FieldDeploymentRepository fieldDeployments;
    private final WorkConfirmationRepository workConfirmations;
    private final CompanyService companyService;
    private final CompanyRepository companyRepo;
    private final SiteRepository siteRepo;
    private final DocumentReviewRepository documentReviews;
    private final DocumentReviewItemRepository documentReviewItems;

    /** 공급사/협력사 본인+직속 자식 소유 자원의 파이프라인 상태. 그 외 역할/회사미상은 빈 목록(readiness 와 동일). */
    @Transactional(readOnly = true)
    public List<ResourcePipelineResponse> listForActor(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            return List.of();
        }
        if (actor.companyId() == null) return List.of();

        List<Long> scope = companyService.selfAndChildren(actor.companyId());
        List<Equipment> equipment = equipmentRepo.findBySupplierIdInOrderByIdDesc(scope);
        List<Person> persons = personRepo.findBySupplierIdInOrderByIdDesc(scope);
        List<Long> equipIds = equipment.stream().map(Equipment::getId).toList();
        List<Long> personIds = persons.stream().map(Person::getId).toList();

        // 3) 투입대기 — readiness 그대로 재사용, (type,id) 로 인덱싱.
        Map<String, ResourceReadinessResponse> readinessMap = readinessService.listForActor(actor).stream()
                .collect(Collectors.toMap(r -> key(r.resourceType(), r.resourceId()), r -> r, (a, b) -> a));

        // 2) 검사 — 자원 id 일괄 조회(readiness 와 동일 배치, N+1 회피).
        Map<Long, List<ResourceCheckRequest>> eqChecks = groupChecks(OwnerType.EQUIPMENT, equipIds);
        Map<Long, List<SafetyInspection>> eqInsp = groupInspections(InspectionTarget.VEHICLE, equipIds);
        Map<Long, List<ResourceCheckRequest>> pChecks = groupChecks(OwnerType.PERSON, personIds);
        Map<Long, List<SafetyInspection>> pInsp = groupInspections(InspectionTarget.PERSON, personIds);

        // 4)/6) 투입·정산 — 배차행(정산과 동일 격리) + FieldDeployment ACTIVE 를 id 집합으로 배치화.
        Set<Long> dispatchedEquipIds = dispatchedEquipment.findAllVisibleForSupplier(scope, actor.companyId()).stream()
                .map(DispatchedEquipment::getEquipmentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> dispatchedPersonIds = dispatchedPerson.findAllVisibleForSupplier(scope, actor.companyId()).stream()
                .map(DispatchedPerson::getPersonId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<FieldDeploymentRequest> active = fieldDeployments
                .findBySupplierCompanyIdInAndStatus(scope, FieldDeploymentStatus.ACTIVE);
        Set<Long> activeEquipIds = active.stream()
                .filter(f -> f.getResourceType() == OwnerType.EQUIPMENT)
                .map(FieldDeploymentRequest::getResourceId).collect(Collectors.toSet());
        Set<Long> activePersonIds = active.stream()
                .filter(f -> f.getResourceType() == OwnerType.PERSON)
                .map(FieldDeploymentRequest::getResourceId).collect(Collectors.toSet());

        // 필터 라벨(업체·현장) — 배치 조회. 이름만 붙일 뿐 스코프/게이트에 영향 없음.
        Set<Long> supplierIds = new HashSet<>();
        equipment.forEach(e -> supplierIds.add(e.getSupplierId()));
        persons.forEach(p -> supplierIds.add(p.getSupplierId()));
        Map<Long, String> supplierNames = companyRepo.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Company::getId, Company::getName));
        Set<Long> siteIds = new HashSet<>();
        equipment.forEach(e -> { if (e.getCurrentSiteId() != null) siteIds.add(e.getCurrentSiteId()); });
        persons.forEach(p -> { if (p.getCurrentSiteId() != null) siteIds.add(p.getCurrentSiteId()); });
        Map<Long, String> siteNames = siteRepo.findAllById(siteIds).stream()
                .collect(Collectors.toMap(Site::getId, Site::getName));

        // 5) 작업 — 인력별 최근 workDate 배치 집계(장비는 해당없음).
        Map<Long, LocalDate> latestWork = new HashMap<>();
        if (!personIds.isEmpty()) {
            for (WorkConfirmation wc : workConfirmations.findByPersonIdIn(personIds)) {
                latestWork.merge(wc.getPersonId(), wc.getWorkDate(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        // 심사 — 공급사가 보낸 DocumentReview(봉투) 중 이 자원이 담긴 최신 봉투 상태(표시용, 게이트 아님).
        Map<String, DocumentReview> latestReviewByOwner = new HashMap<>();
        List<DocumentReview> reviews = documentReviews.findBySupplierCompanyIdInOrderByIdDesc(scope);
        if (!reviews.isEmpty()) {
            Map<Long, DocumentReview> reviewById = reviews.stream()
                    .collect(Collectors.toMap(DocumentReview::getId, r -> r));
            for (DocumentReviewItem item : documentReviewItems.findByReviewIdInOrderByIdAsc(
                    reviews.stream().map(DocumentReview::getId).toList())) {
                String k = key(item.getOwnerType().name(), item.getOwnerId());
                DocumentReview cand = reviewById.get(item.getReviewId());
                DocumentReview cur = latestReviewByOwner.get(k);
                if (cur == null || cand.getId() > cur.getId()) latestReviewByOwner.put(k, cand);
            }
        }

        List<ResourcePipelineResponse> out = new ArrayList<>();
        for (Equipment e : equipment) {
            Long id = e.getId();
            Stages stages = new Stages(
                    docsStage(OwnerType.EQUIPMENT, id, actor),
                    inspectionStage(eqChecks.getOrDefault(id, List.of()), eqInsp.getOrDefault(id, List.of())),
                    readinessStage(readinessMap.get(key("EQUIPMENT", id))),
                    deployedStage(dispatchedEquipIds.contains(id), activeEquipIds.contains(id)),
                    workStage(false, null),
                    settlementStage(dispatchedEquipIds.contains(id)));
            DocumentReview rv = latestReviewByOwner.get(key("EQUIPMENT", id));
            out.add(new ResourcePipelineResponse("EQUIPMENT", id, equipmentLabel(e),
                    e.getSupplierId(), supplierNames.get(e.getSupplierId()),
                    e.getCurrentSiteId(), siteNames.get(e.getCurrentSiteId()),
                    reviewStatusOf(rv), reviewReasonOf(rv), stages));
        }
        for (Person p : persons) {
            Long id = p.getId();
            Stages stages = new Stages(
                    docsStage(OwnerType.PERSON, id, actor),
                    inspectionStage(pChecks.getOrDefault(id, List.of()), pInsp.getOrDefault(id, List.of())),
                    readinessStage(readinessMap.get(key("PERSON", id))),
                    deployedStage(dispatchedPersonIds.contains(id), activePersonIds.contains(id)),
                    workStage(true, latestWork.get(id)),
                    settlementStage(dispatchedPersonIds.contains(id)));
            DocumentReview rv = latestReviewByOwner.get(key("PERSON", id));
            out.add(new ResourcePipelineResponse("PERSON", id, personLabel(p),
                    p.getSupplierId(), supplierNames.get(p.getSupplierId()),
                    p.getCurrentSiteId(), siteNames.get(p.getCurrentSiteId()),
                    reviewStatusOf(rv), reviewReasonOf(rv), stages));
        }
        return out;
    }

    /** 1) 서류 — ComplianceService 재사용(자원별 ResourceCompliance). readyForWorkPlan=완료. */
    private Stage docsStage(OwnerType type, Long id, AuthenticatedUser actor) {
        ResourceCompliance rc = type == OwnerType.EQUIPMENT
                ? complianceService.forEquipment(id, actor)
                : complianceService.forPerson(id, actor);
        if (rc.readyForWorkPlan()) {
            return new Stage(DONE, rc.expiringCount() > 0 ? "완비 · 만료임박 " + rc.expiringCount() : "필수서류 완비");
        }
        List<String> parts = new ArrayList<>();
        if (rc.missingCount() > 0) parts.add("누락 " + rc.missingCount());
        if (rc.rejectedCount() > 0) parts.add("반려 " + rc.rejectedCount());
        if (rc.expiringCount() > 0) parts.add("만료임박 " + rc.expiringCount());
        return new Stage(PENDING, parts.isEmpty() ? "검증 대기" : String.join(" · ", parts));
    }

    /** 2) 검사 — ResourceCheck(신청/제출/승인) + SafetyInspection(일정/완료). 둘 다 없으면 미신청. */
    private Stage inspectionStage(List<ResourceCheckRequest> checks, List<SafetyInspection> insps) {
        if (checks.isEmpty() && insps.isEmpty()) return new Stage(PENDING, "검사 미신청");
        // 점검 완료 술어 = APPROVED/CANCELLED 만 통과(readiness 와 동일). 없으면 vacuously 통과.
        boolean checksDone = checks.stream().allMatch(c ->
                c.getStatus() == ResourceCheckStatus.APPROVED || c.getStatus() == ResourceCheckStatus.CANCELLED);
        boolean safetyDone = insps.stream().anyMatch(s -> s.getStatus() == InspectionStatus.COMPLETED);
        List<String> parts = new ArrayList<>();
        if (!checks.isEmpty()) {
            long approved = checks.stream().filter(c -> c.getStatus() == ResourceCheckStatus.APPROVED).count();
            boolean anyRejected = checks.stream().anyMatch(c -> c.getStatus() == ResourceCheckStatus.REJECTED);
            parts.add(checksDone ? "점검 승인"
                    : anyRejected ? "점검 반려"
                    : "점검 " + approved + "/" + checks.size());
        }
        if (!insps.isEmpty()) parts.add(safetyDone ? "안전검사 완료" : "안전검사 대기");
        return new Stage(checksDone && safetyDone ? DONE : PENDING, String.join(" · ", parts));
    }

    /** 3) 투입대기 — readiness 판정 그대로. (라벨은 검사 목록 "투입 준비됨" 뱃지와 정합.) */
    private Stage readinessStage(ResourceReadinessResponse r) {
        if (r == null) return new Stage(PENDING, "확인 불가");
        if (r.ready()) return new Stage(DONE, "투입 준비됨");
        return new Stage(PENDING, r.pending().isEmpty() ? "준비중" : String.join(" · ", r.pending()));
    }

    /** 4) 투입 — FieldDeployment ACTIVE(현장 투입중) 우선, 없으면 배차행 존재(배차됨). */
    private Stage deployedStage(boolean dispatched, boolean active) {
        if (active) return new Stage(DONE, "현장 투입중");
        if (dispatched) return new Stage(DONE, "배차됨");
        return new Stage(PENDING, "미투입");
    }

    /** 5) 작업 — 인력 최근 작업확인서. 장비는 해당없음. */
    private Stage workStage(boolean isPerson, LocalDate latest) {
        if (!isPerson) return new Stage(NA, "해당없음");
        if (latest == null) return new Stage(PENDING, "작업확인서 없음");
        return new Stage(DONE, "최근 작업 " + latest);
    }

    /** 6) 정산 — 배차행 있으면 정산대상(금액 계산 미재현). */
    private Stage settlementStage(boolean dispatched) {
        return dispatched ? new Stage(DONE, "정산 대상") : new Stage(PENDING, "정산 없음");
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

    /** readiness 와 동일 라벨 규칙: model → vehicleNo → "장비 #id". */
    private String equipmentLabel(Equipment e) {
        if (e.getModel() != null) return e.getModel();
        if (e.getVehicleNo() != null) return e.getVehicleNo();
        return "장비 #" + e.getId();
    }

    private String personLabel(Person p) {
        return p.getName() != null ? p.getName() : "인원 #" + p.getId();
    }

    private static String key(String type, Long id) {
        return type + ":" + id;
    }

    private static String reviewStatusOf(DocumentReview r) {
        return r == null ? null : r.getStatus().name();
    }

    private static String reviewReasonOf(DocumentReview r) {
        return r == null ? null : r.getRejectedReason();
    }
}
