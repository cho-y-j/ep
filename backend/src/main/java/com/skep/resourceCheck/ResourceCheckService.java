package com.skep.resourceCheck;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentService;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.resourceCheck.dto.IssueRequest;
import com.skep.resourceCheck.dto.ResourceCheckResponse;
import com.skep.resourceCheck.dto.ReviewRequest;
import com.skep.resourceCheck.dto.SubmitRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceCheckService {

    private final ResourceCheckRequestRepository repo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companies;
    private final NotificationService notifications;
    private final DocumentService documentService;
    private final com.skep.collection.DocumentUploadMerger uploadMerger;
    private final DocumentTypeRepository documentTypes;
    private final DocumentRepository docRepo;
    private final AlimTalkService alimTalk;
    private final WorkPlanRepository workPlans;
    private final SiteRepository sites;

    /** BP 가 자원 점검을 공급사에 요청. */
    @Transactional
    public ResourceCheckResponse issue(IssueRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 점검 요청 가능");
        }
        Long bpCompanyId = actor.role() == Role.ADMIN ? req.supplierCompanyId() : actor.companyId();
        // ADMIN 케이스: bpCompanyId 추론 어려움 — 실제로는 workPlan 의 bpCompanyId 또는 별도 필드 필요.
        // 우선 BP 본인 케이스에 집중. ADMIN은 actor.companyId() 가 null 이라 supplier 와 다른 회사여야.
        if (actor.role() == Role.BP) {
            if (bpCompanyId == null) throw ApiException.badRequest("NO_COMPANY", "회사 식별 불가");
            if (bpCompanyId.equals(req.supplierCompanyId())) {
                throw ApiException.badRequest("SAME_COMPANY", "BP 자기 회사에 보낼 수 없습니다");
            }
        }

        var entity = ResourceCheckRequest.issue(
                req.workPlanId(), req.ownerType(), req.ownerId(),
                req.supplierCompanyId(), bpCompanyId != null ? bpCompanyId : 0L,
                req.checkType(), req.dueDate(), req.notes(), actor.id());
        repo.save(entity);

        // 공급사 알림
        String label = ownerLabel(req.ownerType(), req.ownerId());
        String checkLabel = checkTypeLabel(req.checkType());
        notifications.sendToCompany(req.supplierCompanyId(),
                "RESOURCE_CHECK_REQUEST",
                "점검 요청 도착 — " + checkLabel,
                label + " — 마감일: " + (req.dueDate() != null ? req.dueDate().toString() : "미정"),
                "RESOURCE_CHECK", entity.getId(), null, notifications.senderLabelOf(actor));

        // 알림톡 발송 (수신번호 입력 시) — 인앱 알림과 별개로 카카오 알림톡/SMS 통지
        sendAlimtalkIfRequested(req, bpCompanyId != null ? bpCompanyId : 0L, actor);

        return toResponse(entity);
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
        if (actor.role() != Role.ADMIN && !entity.getSupplierCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사의 요청만 회신 가능");
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
        if (actor.role() != Role.ADMIN && !entity.getSupplierCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사의 요청만 회신 가능");
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
            case VEHICLE_SAFETY -> "자동차 안전점검 결과서";
            case HEALTH_CHECK -> "건강검진 결과서";
            case SAFETY_TRAINING -> "안전교육 이수증";
            case OTHER -> ownerType == OwnerType.EQUIPMENT ? "점검 회신 - 기타 (장비)" : "점검 회신 - 기타 (인원)";
        };
        return documentTypes.findByNameAndAppliesTo(typeName, ownerType)
                .map(t -> t.getId())
                .orElseThrow(() -> ApiException.badRequest("DOC_TYPE_NOT_SEEDED",
                        "점검 회신용 문서 타입 미시드: " + typeName));
    }

    @Transactional
    public ResourceCheckResponse approve(Long id, ReviewRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 승인 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        if (actor.role() == Role.BP && !entity.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사의 요청만 승인 가능");
        }
        if (entity.getStatus() != ResourceCheckStatus.SUBMITTED) {
            throw ApiException.badRequest("INVALID_STATE", "회신 완료 상태에서만 승인 가능");
        }
        entity.approve(actor.id(), req != null ? req.note() : null);
        // BP 승인 시 회신 서류도 VERIFIED 처리. 그래야 작업계획서 제출 게이트가 풀림.
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
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 반려 가능");
        }
        var entity = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHECK_NOT_FOUND", "점검 요청 없음"));
        if (actor.role() == Role.BP && !entity.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사의 요청만 반려 가능");
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
        return repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
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
        return ResourceCheckResponse.from(r, ownerLabel, supplierName);
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

    /** 알림톡 발송 요청이 있으면 check_type→템플릿 매핑 후 수신번호별 발송. (AlimTalkService 가 실패 swallow) */
    private void sendAlimtalkIfRequested(IssueRequest req, long bpCompanyId, AuthenticatedUser actor) {
        if (req.alimtalkPhones() == null || req.alimtalkPhones().isEmpty()) return;
        AlimTalkTemplate template = switch (req.checkType()) {
            case HEALTH_CHECK -> AlimTalkTemplate.HEALTH_CHECK;
            case VEHICLE_SAFETY -> AlimTalkTemplate.VEHICLE_SAFETY;
            default -> null;  // SAFETY_TRAINING/OTHER — 등록 템플릿 없음
        };
        if (template == null) return;

        // ADMIN 발행 케이스는 bpCompanyId 가 supplier 로 폴백되므로(기존 로직) 그 경우 BP사 공란.
        String bpName = (bpCompanyId != 0L && bpCompanyId != req.supplierCompanyId())
                ? companyName(bpCompanyId) : "";
        Map<String, String> vars = new HashMap<>();
        vars.put("업체명", companyName(req.supplierCompanyId()));
        vars.put("BP사", bpName);
        vars.put("현장명", siteName(req.workPlanId()));
        vars.put("요청기한", req.dueDate() != null ? req.dueDate().toString() : "미정");
        if (template == AlimTalkTemplate.HEALTH_CHECK) {
            vars.put("대상자명", ownerLabel(req.ownerType(), req.ownerId()));
        } else {
            vars.put("차량번호", vehicleNo(req.ownerId()));
        }
        for (String phone : req.alimtalkPhones()) {
            alimTalk.send(phone, template, vars, actor.id());
        }
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
            case VEHICLE_SAFETY -> "자동차 안전점검";
            case HEALTH_CHECK -> "건강검진";
            case SAFETY_TRAINING -> "안전교육";
            case OTHER -> "기타";
        };
    }
}
