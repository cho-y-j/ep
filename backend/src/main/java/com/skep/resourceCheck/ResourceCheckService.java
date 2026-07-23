package com.skep.resourceCheck;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentService;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.resourceCheck.dto.IssueComboRequest;
import com.skep.resourceCheck.dto.IssueRequest;
import com.skep.resourceCheck.dto.ResourceCheckResponse;
import com.skep.resourceCheck.dto.ReviewRequest;
import com.skep.resourceCheck.dto.SubmitRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.alimtalk.AlimTalkService;
import com.skep.alimtalk.AlimTalkTemplate;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceCheckService {

    private final ResourceCheckRequestRepository repo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companies;
    private final CompanyService companyService;
    private final NotificationService notifications;
    private final DocumentService documentService;
    private final com.skep.collection.DocumentUploadMerger uploadMerger;
    private final DocumentTypeRepository documentTypes;
    private final DocumentRepository docRepo;
    private final AlimTalkService alimTalk;
    private final WorkPlanRepository workPlans;
    private final SiteRepository sites;
    private final UserRepository users;

    /** BP·공급사·ADMIN 이 자원 점검을 발행. bp_company_id = 발행사(BP 발행이면 BP사, 공급사 발행이면 자기 회사). */
    @Transactional
    public ResourceCheckResponse issue(IssueRequest req, AuthenticatedUser actor) {
        Long bpCompanyId = requireIssueScope(actor, req.supplierCompanyId(),
                List.of(new OwnerRef(req.ownerType(), req.ownerId())));

        var entity = createRow(req.workPlanId(), req.ownerType(), req.ownerId(),
                req.supplierCompanyId(), bpCompanyId != null ? bpCompanyId : 0L,
                req.checkType(), req.dueDate(), req.dueTime(), req.notes(), actor.id(), null);

        // 공급사 알림
        String label = ownerLabel(req.ownerType(), req.ownerId());
        String checkLabel = checkTypeLabel(req.checkType());
        notifications.sendToCompany(req.supplierCompanyId(),
                "RESOURCE_CHECK_REQUEST",
                "점검 요청 도착 — " + checkLabel,
                label + " — 마감일: " + dueLabel(req.dueDate(), req.dueTime()),
                "RESOURCE_CHECK", entity.getId(), null, notifications.senderLabelOf(actor));

        // 알림톡 — 수신 공급사 담당자(소속 사용자 등록번호)에게 자동 발송 + 다이얼로그 입력 추가 수신번호.
        sendIssueAlimtalk(req, bpCompanyId != null ? bpCompanyId : 0L, actor);

        return toResponse(entity);
    }

    /**
     * R2: 조합(장비+교대조 조종원) 일괄 발행 — 단일 트랜잭션으로 장비 1×N종 + 조종원 N×M종 행 생성.
     * 전 행에 combo_equipment_id 스냅샷. 가드는 단건 issue 와 공용(requireIssueScope) — 자원 1건이라도
     * 스코프 밖이면 403 전체 롤백. 조종원의 equipment_default_operators 소속 여부는 강제하지 않음(임시 교대 현실).
     * 공급사 수신 알림은 행당 N건 대신 묶음 1건.
     */
    @Transactional
    public List<ResourceCheckResponse> issueCombo(IssueComboRequest req, AuthenticatedUser actor) {
        List<Long> operatorIds = req.operatorPersonIds() == null ? List.of()
                : req.operatorPersonIds().stream().distinct().toList();
        List<ResourceCheckType> equipmentChecks = req.checks().equipment() != null ? req.checks().equipment() : List.of();
        List<ResourceCheckType> operatorChecks = req.checks().operator() != null ? req.checks().operator() : List.of();
        if (equipmentChecks.isEmpty() && (operatorIds.isEmpty() || operatorChecks.isEmpty())) {
            throw ApiException.badRequest("NO_CHECKS", "발행할 점검이 없습니다");
        }

        List<OwnerRef> owners = new ArrayList<>();
        owners.add(new OwnerRef(OwnerType.EQUIPMENT, req.equipmentId()));
        operatorIds.forEach(pid -> owners.add(new OwnerRef(OwnerType.PERSON, pid)));
        Long bpCompanyId = requireIssueScope(actor, req.supplierCompanyId(), owners);
        long issuerCompanyId = bpCompanyId != null ? bpCompanyId : 0L;

        Equipment equipment = equipmentRepo.findById(req.equipmentId()).orElseThrow(() ->
                ApiException.notFound("RESOURCE_NOT_FOUND", "대상 자원 없음"));

        List<ResourceCheckRequest> rows = new ArrayList<>();
        for (ResourceCheckType t : equipmentChecks) {
            rows.add(createRow(req.workPlanId(), OwnerType.EQUIPMENT, req.equipmentId(),
                    req.supplierCompanyId(), issuerCompanyId, t, req.dueDate(), req.dueTime(), req.notes(),
                    actor.id(), req.equipmentId()));
        }
        for (Long pid : operatorIds) {
            for (ResourceCheckType t : operatorChecks) {
                rows.add(createRow(req.workPlanId(), OwnerType.PERSON, pid,
                        req.supplierCompanyId(), issuerCompanyId, t, req.dueDate(), req.dueTime(), req.notes(),
                        actor.id(), req.equipmentId()));
            }
        }

        // 공급사 알림 — 묶음 1건: 『차량번호』 조합 점검 N건 — 종류 요약. 링크는 첫 행(수신함 진입용).
        String comboLabel = equipment.getVehicleNo() != null && !equipment.getVehicleNo().isBlank()
                ? equipment.getVehicleNo()
                : (equipment.getModel() != null ? equipment.getModel() : "장비 #" + equipment.getId());
        String typeSummary = rows.stream().map(ResourceCheckRequest::getCheckType).distinct()
                .map(ResourceCheckService::checkTypeLabel).collect(Collectors.joining("·"));
        String operatorNames = personRepo.findAllById(operatorIds).stream()
                .map(Person::getName).collect(Collectors.joining(", "));
        notifications.sendToCompany(req.supplierCompanyId(),
                "RESOURCE_CHECK_REQUEST",
                "『" + comboLabel + "』 조합 점검 " + rows.size() + "건 — " + typeSummary,
                (operatorNames.isBlank() ? "" : "조종원: " + operatorNames + " — ")
                        + "마감일: " + dueLabel(req.dueDate(), req.dueTime()),
                "RESOURCE_CHECK", rows.get(0).getId(), null, notifications.senderLabelOf(actor));

        // 알림톡 — 조합도 행당 N건이 아니라 묶음 1건. 공급사 담당자 자동 수신.
        sendComboAlimtalk(req, equipmentChecks, operatorChecks, operatorNames, issuerCompanyId, actor);

        return rows.stream().map(this::toResponse).toList();
    }

    /** 발행 대상 자원 참조 — 단건·조합 공용 가드 인자. */
    private record OwnerRef(OwnerType type, Long id) {}

    /**
     * 발행 주체·스코프 가드 — 단건(issue)·조합(issueCombo) 공용. 반환 = bp_company_id 저장값(발행사 회사 id).
     * ADMIN 케이스: bpCompanyId 추론 어려움 — 실제로는 workPlan 의 bpCompanyId 또는 별도 필드 필요.
     * 우선 BP 본인 케이스에 집중. ADMIN은 actor.companyId() 가 null 이라 supplier 와 다른 회사여야.
     */
    private Long requireIssueScope(AuthenticatedUser actor, Long supplierCompanyId, List<OwnerRef> owners) {
        boolean supplierIssuer = actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP && !supplierIssuer) {
            throw ApiException.forbidden("ISSUER_ONLY", "BP/공급사/ADMIN 만 점검 요청 가능");
        }
        Long bpCompanyId = actor.role() == Role.ADMIN ? supplierCompanyId : actor.companyId();
        if (actor.role() == Role.BP) {
            if (bpCompanyId == null) throw ApiException.badRequest("NO_COMPANY", "회사 식별 불가");
            if (bpCompanyId.equals(supplierCompanyId)) {
                throw ApiException.badRequest("SAME_COMPANY", "BP 자기 회사에 보낼 수 없습니다");
            }
        }
        // 공급사 발행(BP 미사용 현실 대응): 자기 회사(+직속 자식 협력사) 자원에만 발행 가능.
        // SAME_COMPANY 가드는 미적용 — 자기/자식 자원 대상 발행이 정상 흐름. bp_company_id 에는 발행사(자기 회사) id 저장.
        if (supplierIssuer) {
            if (bpCompanyId == null) throw ApiException.badRequest("NO_COMPANY", "회사 식별 불가");
            List<Long> scope = companyService.selfAndChildren(bpCompanyId);
            if (!scope.contains(supplierCompanyId)) {
                throw ApiException.forbidden("NOT_MY_RESOURCE", "자기 회사(협력사 포함) 자원에만 발행할 수 있습니다");
            }
            for (OwnerRef o : owners) {
                if (!scope.contains(resourceSupplierId(o.type(), o.id()))) {
                    throw ApiException.forbidden("NOT_MY_RESOURCE", "자기 회사(협력사 포함) 자원에만 발행할 수 있습니다");
                }
            }
        }
        return bpCompanyId;
    }

    /**
     * 행 생성 — 단건(issue)·조합(issueCombo) 공용.
     * [판정 이원화 해소] 재검사 재발행: 같은 자원·같은 check_type 의 기존 REJECTED 건을 자동 CANCELLED.
     * readiness/파이프라인/작업계획서 제출 술어는 "발행된 점검 전부 APPROVED(CANCELLED 는 통과)"라
     * 반려 건이 남으면 새 건을 승인해도 영구 미준비 — CANCELLED 는 기존 상태값 그대로(스키마 무변경).
     */
    private ResourceCheckRequest createRow(Long workPlanId, OwnerType ownerType, Long ownerId,
                                           Long supplierCompanyId, Long bpCompanyId,
                                           ResourceCheckType checkType, LocalDate dueDate, LocalTime dueTime,
                                           String notes, Long issuedBy, Long comboEquipmentId) {
        repo.findByOwnerTypeAndOwnerIdOrderByIdDesc(ownerType, ownerId).stream()
                .filter(c -> c.getCheckType() == checkType
                        && c.getStatus() == ResourceCheckStatus.REJECTED)
                .forEach(ResourceCheckRequest::cancel);

        var entity = ResourceCheckRequest.issue(workPlanId, ownerType, ownerId,
                supplierCompanyId, bpCompanyId, checkType, dueDate, notes, issuedBy);
        entity.setDueTime(dueTime);
        entity.setComboEquipmentId(comboEquipmentId);
        return repo.save(entity);
    }

    /** 공급사가 회신 — document 첨부. */
    @Transactional
    public ResourceCheckResponse submit(Long id, SubmitRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER
                && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 회신 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        // V77: 부모가 자식(협력사) 수신분도 회신 가능. 형제/무관사는 selfAndChildren 밖이라 403 유지.
        if (actor.role() != Role.ADMIN
                && !companyService.selfAndChildren(actor.companyId()).contains(entity.getSupplierCompanyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사(협력사 포함)의 요청만 회신 가능");
        }
        if (entity.getStatus() != ResourceCheckStatus.REQUESTED
                && entity.getStatus() != ResourceCheckStatus.REJECTED) {
            throw ApiException.badRequest("INVALID_STATE",
                    "현재 상태에서는 회신 불가: " + entity.getStatus());
        }
        entity.submit(req.documentId());

        // BP 알림
        notifications.sendToCompany(entity.getBpCompanyId(),
                "RESOURCE_CHECK_SUBMITTED",
                "점검 결과 회신 도착",
                ownerLabel(entity.getOwnerType(), entity.getOwnerId()) + " — " + checkTypeLabel(entity.getCheckType()),
                "RESOURCE_CHECK", entity.getId(), null, notifications.senderLabelOf(actor));

        return toResponse(entity);
    }

    /** 공급사 회신 — 파일 직접 업로드. document_type 은 check_type 으로 매핑.
     *  파일 1개면 그대로, 2개 이상이면 올린 순서대로 1개 PDF로 병합 후 저장. */
    @Transactional
    public ResourceCheckResponse submitWithFile(Long id, MultipartFile[] files, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER
                && actor.role() != Role.MANPOWER_SUPPLIER
                && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 회신 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        // V77: 부모가 자식(협력사) 수신분도 회신 가능. 형제/무관사는 selfAndChildren 밖이라 403 유지.
        if (actor.role() != Role.ADMIN
                && !companyService.selfAndChildren(actor.companyId()).contains(entity.getSupplierCompanyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사(협력사 포함)의 요청만 회신 가능");
        }
        if (entity.getStatus() != ResourceCheckStatus.REQUESTED
                && entity.getStatus() != ResourceCheckStatus.REJECTED) {
            throw ApiException.badRequest("INVALID_STATE",
                    "현재 상태에서는 회신 불가: " + entity.getStatus());
        }
        Long documentTypeId = resolveDocumentTypeId(entity.getCheckType(), entity.getOwnerType());
        MultipartFile file = uploadMerger.mergeToSingle(files);
        var doc = documentService.upload(entity.getOwnerType(), entity.getOwnerId(),
                documentTypeId, null, file, actor);
        entity.submit(doc.id());

        notifications.sendToCompany(entity.getBpCompanyId(),
                "RESOURCE_CHECK_SUBMITTED",
                "점검 결과 회신 도착",
                ownerLabel(entity.getOwnerType(), entity.getOwnerId()) + " — " + checkTypeLabel(entity.getCheckType()),
                "RESOURCE_CHECK", entity.getId(), null, notifications.senderLabelOf(actor));

        return toResponse(entity);
    }

    private Long resolveDocumentTypeId(ResourceCheckType checkType, OwnerType ownerType) {
        String typeName = switch (checkType) {
            case VEHICLE_SAFETY -> "자동차 반입검사 결과서";  // V125 에서 개명(구명은 V125 헤더 참조)
            case HEALTH_CHECK -> "건강검진 결과서";
            case SAFETY_TRAINING -> "안전교육 이수증";
            case OTHER -> ownerType == OwnerType.EQUIPMENT ? "점검 회신 - 기타 (장비)" : "점검 회신 - 기타 (인원)";
        };
        return documentTypes.findByNameAndAppliesTo(typeName, ownerType)
                .map(t -> t.getId())
                .orElseThrow(() -> ApiException.badRequest("DOC_TYPE_NOT_SEEDED",
                        "점검 회신용 문서 타입 미시드: " + typeName));
    }

    /** 승인 주체 = 발행사(bp_company_id — BP 또는 공급사 자신)/ADMIN. 승인자·시각은 reviewed_by/reviewed_at 로 기록. */
    @Transactional
    public ResourceCheckResponse approve(Long id, ReviewRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP
                && actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("ISSUER_ADMIN_ONLY", "발행사/ADMIN 만 승인 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        if (actor.role() != Role.ADMIN && !entity.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "발행사(본인 회사)의 요청만 승인 가능");
        }
        if (entity.getStatus() != ResourceCheckStatus.SUBMITTED) {
            throw ApiException.badRequest("INVALID_STATE", "회신 완료 상태에서만 승인 가능");
        }
        entity.approve(actor.id(), req != null ? req.note() : null);
        // 발행사(공급사 포함) 승인 시 회신 서류도 VERIFIED 처리(정책 유지). 승인자는 verified_by 로 기록.
        // 그래야 작업계획서 제출 게이트가 풀림.
        if (entity.getDocumentId() != null) {
            docRepo.findById(entity.getDocumentId()).ifPresent(d -> {
                d.markVerifiedBy(actor.id());
                docRepo.save(d);
            });
        }
        return toResponse(entity);
    }

    @Transactional
    public ResourceCheckResponse reject(Long id, ReviewRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP
                && actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("ISSUER_ADMIN_ONLY", "발행사/ADMIN 만 반려 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        if (actor.role() != Role.ADMIN && !entity.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "발행사(본인 회사)의 요청만 반려 가능");
        }
        if (entity.getStatus() != ResourceCheckStatus.SUBMITTED) {
            throw ApiException.badRequest("INVALID_STATE", "회신 완료 상태에서만 반려 가능");
        }
        entity.reject(actor.id(), req != null ? req.note() : null);

        // 공급사 알림 — 재제출 요청
        notifications.sendToCompany(entity.getSupplierCompanyId(),
                "RESOURCE_CHECK_REJECTED",
                "점검 회신 반려됨 — 재제출 필요",
                ownerLabel(entity.getOwnerType(), entity.getOwnerId()) + " — " + checkTypeLabel(entity.getCheckType()),
                "RESOURCE_CHECK", entity.getId(), null, notifications.senderLabelOf(actor));

        return toResponse(entity);
    }

    /** V125 통화·연락 기록 — 발행사(bp_company_id 소속 공급사·BP)/ADMIN 만. "[7/24 14:00 이름] 내용" 줄 append. */
    @Transactional
    public ResourceCheckResponse addContactLog(Long id, String note, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP
                && actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("ISSUER_ADMIN_ONLY", "발행사/ADMIN 만 기록 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        if (actor.role() != Role.ADMIN && !entity.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "발행사(본인 회사)의 요청만 기록 가능");
        }
        if (note == null || note.isBlank()) {
            throw ApiException.badRequest("EMPTY_NOTE", "기록 내용을 입력하세요");
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d HH:mm"));
        String who = actor.name() != null && !actor.name().isBlank() ? actor.name() : "담당자";
        String line = "[" + stamp + " " + who + "] " + note.trim();
        entity.setContactLog(entity.getContactLog() == null || entity.getContactLog().isBlank()
                ? line : entity.getContactLog() + "\n" + line);
        return toResponse(entity);
    }

    /** 발행 목록 — bp_company_id = 발행사라 공급사 발행분도 자기 회사 분기로 그대로 조회. findAll 은 ADMIN 전용. */
    @Transactional(readOnly = true)
    public List<ResourceCheckResponse> listForBp(AuthenticatedUser actor) {
        Long bpId = actor.role() == Role.ADMIN ? null : actor.companyId();
        var list = bpId != null
                ? repo.findByBpCompanyIdOrderByIdDesc(bpId)
                : repo.findAll();
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ResourceCheckResponse> listForSupplier(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        // V77: 부모가 자식(협력사) 수신분도 보고 회신할 수 있게 self+children 확장.
        return repo.findBySupplierCompanyIdInOrderByIdDesc(companyService.selfAndChildren(actor.companyId())).stream()
                .map(this::toResponse).toList();
    }

    /**
     * 작업계획서별 점검 요청. H-2: actor 스코프 적용 —
     * ADMIN 전체, BP 는 자기 bp_company_id 발송분, 공급사는 자기 supplier_company_id 수신분만.
     * (그 외 역할/회사 미상은 빈 목록.)
     */
    @Transactional(readOnly = true)
    public List<ResourceCheckResponse> listForWorkPlan(Long workPlanId, AuthenticatedUser actor) {
        var rows = repo.findByWorkPlanIdOrderByIdDesc(workPlanId);
        return rows.stream()
                .filter(r -> canView(r, actor))
                .map(this::toResponse).toList();
    }

    private boolean canView(ResourceCheckRequest r, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return true;
        Long companyId = actor.companyId();
        if (companyId == null) return false;
        if (actor.role() == Role.BP) return companyId.equals(r.getBpCompanyId());
        if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            return companyId.equals(r.getSupplierCompanyId());
        }
        return false;
    }

    private ResourceCheckResponse toResponse(ResourceCheckRequest r) {
        String ownerLabel = ownerLabel(r.getOwnerType(), r.getOwnerId());
        String supplierName = companies.findById(r.getSupplierCompanyId())
                .map(Company::getName).orElse(null);
        // R2: 조합 스냅샷 행이면 목록 묶음 헤더용 장비 라벨 동봉.
        String comboLabel = r.getComboEquipmentId() != null
                ? ownerLabel(OwnerType.EQUIPMENT, r.getComboEquipmentId()) : null;
        return ResourceCheckResponse.from(r, ownerLabel, supplierName, comboLabel);
    }

    /** 공급사 발행 스코프 가드용 — 대상 자원의 실제 보유사 id. */
    private Long resourceSupplierId(OwnerType type, Long ownerId) {
        if (type == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(ownerId).map(Equipment::getSupplierId)
                    .orElseThrow(() -> ApiException.notFound("RESOURCE_NOT_FOUND", "대상 자원 없음"));
        }
        if (type == OwnerType.PERSON) {
            return personRepo.findById(ownerId).map(Person::getSupplierId)
                    .orElseThrow(() -> ApiException.notFound("RESOURCE_NOT_FOUND", "대상 자원 없음"));
        }
        throw ApiException.badRequest("UNSUPPORTED_OWNER", "장비/인원 자원만 점검 발행 가능");
    }

    private String ownerLabel(OwnerType type, Long id) {
        if (type == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(id)
                    .map(e -> {
                        Equipment eq = e;
                        String name = eq.getModel() != null ? eq.getModel()
                                : (eq.getVehicleNo() != null ? eq.getVehicleNo() : "장비 #" + id);
                        return name;
                    }).orElse("장비 #" + id);
        }
        if (type == OwnerType.PERSON) {
            return personRepo.findById(id)
                    .map(Person::getName)
                    .orElse("인원 #" + id);
        }
        return type.name() + " #" + id;
    }

    /** 단건 발행 알림톡 — 수신 공급사 담당자 자동 + 추가 수신번호. check_type→템플릿 매핑(미등록 종류는 미발송). */
    private void sendIssueAlimtalk(IssueRequest req, long bpCompanyId, AuthenticatedUser actor) {
        AlimTalkTemplate template = switch (req.checkType()) {
            case HEALTH_CHECK -> AlimTalkTemplate.HEALTH_CHECK;
            case VEHICLE_SAFETY -> AlimTalkTemplate.VEHICLE_SAFETY;
            default -> null;  // SAFETY_TRAINING/OTHER — 등록 템플릿 없음
        };
        if (template == null) return;

        Map<String, String> vars = alimtalkBaseVars(req.supplierCompanyId(), bpCompanyId,
                req.workPlanId(), req.dueDate(), req.dueTime());
        if (template == AlimTalkTemplate.HEALTH_CHECK) {
            vars.put("대상자명", ownerLabel(req.ownerType(), req.ownerId()));
        } else {
            vars.put("차량번호", vehicleNo(req.ownerId()));
        }
        dispatchAlimtalk(template, vars, req.supplierCompanyId(), req.alimtalkPhones(), actor);
    }

    /** 조합 발행 알림톡 — 묶음 1건. 장비 반입검사 템플릿 우선, 없으면 건강검진 템플릿(승인 템플릿 2종뿐). */
    private void sendComboAlimtalk(IssueComboRequest req, List<ResourceCheckType> equipmentChecks,
                                   List<ResourceCheckType> operatorChecks, String operatorNames,
                                   long issuerCompanyId, AuthenticatedUser actor) {
        AlimTalkTemplate template;
        if (equipmentChecks.contains(ResourceCheckType.VEHICLE_SAFETY)) {
            template = AlimTalkTemplate.VEHICLE_SAFETY;
        } else if (operatorChecks.contains(ResourceCheckType.HEALTH_CHECK) && !operatorNames.isBlank()) {
            template = AlimTalkTemplate.HEALTH_CHECK;
        } else {
            return;  // 등록 템플릿 없는 종류만 발행됨
        }

        Map<String, String> vars = alimtalkBaseVars(req.supplierCompanyId(), issuerCompanyId,
                req.workPlanId(), req.dueDate(), req.dueTime());
        if (template == AlimTalkTemplate.HEALTH_CHECK) {
            vars.put("대상자명", operatorNames);
        } else {
            vars.put("차량번호", vehicleNo(req.equipmentId()));
        }
        dispatchAlimtalk(template, vars, req.supplierCompanyId(), null, actor);
    }

    /** 알림톡 공통 변수 — 발행사가 공급사 자신이면 BP사 공란(기존 정책 유지). */
    private Map<String, String> alimtalkBaseVars(Long supplierCompanyId, long issuerCompanyId,
                                                 Long workPlanId, LocalDate dueDate, LocalTime dueTime) {
        String bpName = (issuerCompanyId != 0L && !Long.valueOf(issuerCompanyId).equals(supplierCompanyId))
                ? companyName(issuerCompanyId) : "";
        Map<String, String> vars = new HashMap<>();
        vars.put("업체명", companyName(supplierCompanyId));
        vars.put("BP사", bpName);
        vars.put("현장명", siteName(workPlanId));
        vars.put("요청기한", dueLabel(dueDate, dueTime));
        return vars;
    }

    /** 수신자 = 공급사 담당자(소속 사용자 등록 전화번호) 자동 + 추가 수신번호. 번호 전무면 skip 로그(fail-open).
     *  발송 자체는 AlimTalkService @Async 가 처리(키 미설정 dev 는 sms_logs FAILED 로만 남고 흐름 무영향). */
    private void dispatchAlimtalk(AlimTalkTemplate template, Map<String, String> vars,
                                  Long supplierCompanyId, List<String> extraPhones, AuthenticatedUser actor) {
        Set<String> phones = new LinkedHashSet<>();
        for (User u : users.findByCompanyIdOrderByIdAsc(supplierCompanyId)) {
            if (u.getPhone() != null && !u.getPhone().isBlank()) phones.add(u.getPhone().trim());
        }
        if (extraPhones != null) {
            extraPhones.stream().filter(p -> p != null && !p.isBlank()).map(String::trim).forEach(phones::add);
        }
        if (phones.isEmpty()) {
            log.info("검사 통보 알림톡 skip — 공급사 {} 담당자 전화번호 없음", supplierCompanyId);
            return;
        }
        log.info("검사 통보 알림톡 자동 발송 — 공급사 {} 수신 {}건 ({})", supplierCompanyId, phones.size(), template.name());
        for (String phone : phones) {
            alimTalk.send(phone, template, vars, actor.id());
        }
    }

    /** 마감 표기 — "2026-08-01 14:00" / "2026-08-01" / "미정". 알림·인앱 문구 공용. */
    private static String dueLabel(LocalDate d, LocalTime t) {
        if (d == null) return "미정";
        return t != null ? d + " " + t.format(DateTimeFormatter.ofPattern("HH:mm")) : d.toString();
    }

    private String companyName(Long companyId) {
        if (companyId == null) return "";
        return companies.findById(companyId).map(Company::getName).orElse("");
    }

    private String siteName(Long workPlanId) {
        if (workPlanId == null) return "-";
        return workPlans.findById(workPlanId)
                .map(WorkPlan::getSiteId)
                .flatMap(sites::findById)
                .map(Site::getName)
                .orElse("-");
    }

    private String vehicleNo(Long equipmentId) {
        return equipmentRepo.findById(equipmentId)
                .map(Equipment::getVehicleNo)
                .filter(s -> s != null && !s.isBlank())
                .orElse("차량");
    }

    public static String checkTypeLabel(ResourceCheckType t) {
        return switch (t) {
            case VEHICLE_SAFETY -> "자동차 반입검사";
            case HEALTH_CHECK -> "건강검진";
            case SAFETY_TRAINING -> "안전교육";
            case OTHER -> "기타";
        };
    }
}
