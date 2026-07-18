package com.skep.supplement;

import com.skep.audit.AuditLogService;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyService;
import com.skep.document.DocumentType;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.quotation.dispatch.DispatchedEquipmentRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.supplement.dto.CreateSupplementRequest;
import com.skep.supplement.dto.SupplementResponse;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S-11: 서류 보완 요청 서비스.
 *
 * 권한:
 *   - BP: 자기 사이트 + 자기 BP 회사가 발신
 *   - ADMIN: 전체 + 발신
 *   - 공급사 (EQUIPMENT/MANPOWER): 자기 회사가 target 인 요청 read 만
 *
 * 자동 resolve:
 *   - DocumentService.upload 시점에 onDocumentUploaded(...) 호출 → 같은 (owner, type) 의 OPEN 보완 요청을 RESOLVED 로 자동 close.
 */
@Service
@Transactional
public class DocumentSupplementService {

    private final DocumentSupplementRequestRepository repo;
    private final DocumentTypeRepository typeRepo;
    private final DocumentRepository docRepo;
    private final CompanyRepository companies;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditLogService auditLog;
    private final DispatchedEquipmentRepository dispatched;
    private final CompanyService companyService;

    public DocumentSupplementService(DocumentSupplementRequestRepository repo,
                                      DocumentTypeRepository typeRepo,
                                      DocumentRepository docRepo,
                                      CompanyRepository companies,
                                      EquipmentRepository equipmentRepo,
                                      PersonRepository personRepo,
                                      SiteRepository sites,
                                      SiteParticipantRepository participants,
                                      UserRepository users,
                                      NotificationService notifications,
                                      AuditLogService auditLog,
                                      DispatchedEquipmentRepository dispatched,
                                      CompanyService companyService) {
        this.repo = repo;
        this.typeRepo = typeRepo;
        this.docRepo = docRepo;
        this.companies = companies;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.sites = sites;
        this.participants = participants;
        this.users = users;
        this.notifications = notifications;
        this.auditLog = auditLog;
        this.dispatched = dispatched;
        this.companyService = companyService;
    }

    public SupplementResponse create(CreateSupplementRequest req, AuthenticatedUser actor) {
        DocumentSupplementRequest row = createRow(req, actor);
        DocumentType t = typeRepo.findById(req.documentTypeId()).orElse(null);
        notifications.sendToCompany(row.getTargetSupplierCompanyId(),
                NotificationType.SUPPLEMENT_REQUESTED,
                "서류 보완 요청",
                (t != null ? t.getName() : "서류") + " 보완 요청이 도착했습니다" + (req.reason() != null && !req.reason().isBlank()
                        ? " — 사유: " + req.reason() : ""),
                "DOCUMENT_SUPPLEMENT", row.getId(), req.contextSiteId(), notifications.senderLabelOf(actor));
        return toResponse(row);
    }

    /** 다건 보완요청 — 한 트랜잭션에 모두 생성하고 공급사별 알림 1건으로 집계. */
    public List<SupplementResponse> createBatch(List<CreateSupplementRequest> reqs, AuthenticatedUser actor) {
        if (reqs == null || reqs.isEmpty()) {
            throw ApiException.badRequest("EMPTY_ITEMS", "보완요청 항목이 비어 있습니다");
        }
        List<DocumentSupplementRequest> rows = new java.util.ArrayList<>();
        for (CreateSupplementRequest req : reqs) {
            rows.add(createRow(req, actor));
        }
        // 공급사 회사별로 묶어 알림 1건씩
        Map<Long, List<DocumentSupplementRequest>> bySupplier = rows.stream()
                .collect(Collectors.groupingBy(DocumentSupplementRequest::getTargetSupplierCompanyId));
        for (var entry : bySupplier.entrySet()) {
            List<DocumentSupplementRequest> group = entry.getValue();
            List<String> names = group.stream()
                    .map(r -> typeRepo.findById(r.getDocumentTypeId()).map(DocumentType::getName).orElse("서류"))
                    .distinct().toList();
            String summary = names.size() <= 3
                    ? String.join(", ", names)
                    : names.get(0) + ", " + names.get(1) + " 외 " + (names.size() - 2) + "건";
            notifications.sendToCompany(entry.getKey(),
                    NotificationType.SUPPLEMENT_REQUESTED,
                    "서류 보완 요청 " + group.size() + "건",
                    summary + " 보완 요청이 도착했습니다.",
                    "DOCUMENT_SUPPLEMENT", group.get(0).getId(), group.get(0).getContextSiteId(), notifications.senderLabelOf(actor));
        }
        return rows.stream().map(this::toResponse).toList();
    }

    /** 검증 + 저장만 (알림 X). create / createBatch 공용. */
    private DocumentSupplementRequest createRow(CreateSupplementRequest req, AuthenticatedUser actor) {
        boolean isSupplier = actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER;
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN && !isSupplier) {
            throw ApiException.forbidden("REQUEST_DENIED", "BP/ADMIN 만 보완 요청 가능합니다");
        }
        typeRepo.findById(req.documentTypeId())
                .orElseThrow(() -> ApiException.badRequest("DOCUMENT_TYPE_NOT_FOUND",
                        "서류 타입 " + req.documentTypeId() + " 없음"));

        // target 자원의 supplier 회사 결정
        Long supplierCompanyId = resolveSupplierCompanyId(req.targetOwnerType(), req.targetOwnerId());

        // 공급사(부모)는 직속 하위 공급사(자식) 자원에만 보완 요청 가능. 형제/타사/자기자신은 차단.
        if (isSupplier) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "회사가 식별되지 않습니다");
            }
            boolean isDirectChild = companyService.listChildren(actor.companyId()).stream()
                    .anyMatch(c -> c.getId().equals(supplierCompanyId));
            if (!isDirectChild) {
                throw ApiException.forbidden("NOT_CHILD_SUPPLIER", "직속 하위 공급사 자원만 보완 요청할 수 있습니다");
            }
        }

        // BP 권한 경계: contextSiteId 가 본인 회사 소유 사이트인지 + target 공급사가 그 사이트 참여자인지 검증.
        // ADMIN 은 전체 권한이라 검증 skip.
        if (actor.role() == Role.BP) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "회사가 식별되지 않습니다");
            }
            boolean isOwn = supplierCompanyId.equals(actor.companyId());
            // 견적 배차 흐름: 이 공급사가 BP 소유 견적에 차량을 배차했으면 (=서류 묶음 발송 관계) 허용.
            boolean dispatchedToMe = dispatched.existsSupplierDispatchedToBp(supplierCompanyId, actor.companyId());
            if (req.contextSiteId() != null) {
                Site site = sites.findById(req.contextSiteId()).orElseThrow(() ->
                        ApiException.badRequest("SITE_NOT_FOUND", "사이트 없음"));
                if (!actor.companyId().equals(site.getBpCompanyId())) {
                    throw ApiException.forbidden("SITE_SCOPE_DENIED", "본인 회사 사이트가 아닙니다");
                }
                // target 공급사가 사이트 참여 ACTIVE 이거나 BP 자체 자원(같은 회사), 또는 견적 배차 관계여야.
                boolean isParticipant = participants.existsBySiteIdAndCompanyIdAndStatus(
                        req.contextSiteId(), supplierCompanyId, SiteParticipantStatus.ACTIVE);
                if (!isOwn && !isParticipant && !dispatchedToMe) {
                    throw ApiException.forbidden("RESOURCE_SCOPE_DENIED",
                            "사이트 참여 공급사가 아닙니다");
                }
            } else {
                // contextSiteId null 이면 BP 직속 자원 또는 견적 배차 관계 공급사 한정.
                if (!isOwn && !dispatchedToMe) {
                    throw ApiException.forbidden("SITE_CONTEXT_REQUIRED",
                            "외부 공급사 자원은 사이트 컨텍스트가 필요합니다");
                }
            }
        }

        // 중복 발송 방지 — 같은 (owner, type) 의 OPEN 보완요청이 이미 있으면 기존 행을 그대로 반환.
        var existing = repo.findByTargetOwnerTypeAndTargetOwnerIdAndDocumentTypeIdAndStatus(
                req.targetOwnerType(), req.targetOwnerId(), req.documentTypeId(),
                DocumentSupplementStatus.OPEN);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        DocumentSupplementRequest row = DocumentSupplementRequest.builder()
                .requesterUserId(actor.id())
                .requesterRole(actor.role())
                .targetSupplierCompanyId(supplierCompanyId)
                .targetOwnerType(req.targetOwnerType())
                .targetOwnerId(req.targetOwnerId())
                .documentTypeId(req.documentTypeId())
                .contextSiteId(req.contextSiteId())
                .contextWorkPlanId(req.contextWorkPlanId())
                .reason(req.reason())
                .build();
        repo.save(row);
        return row;
    }

    @Transactional(readOnly = true)
    public List<SupplementResponse> list(AuthenticatedUser actor) {
        List<DocumentSupplementRequest> rows;
        if (actor.role() == Role.ADMIN) {
            rows = repo.findAll().stream()
                    .sorted(Comparator.comparing(DocumentSupplementRequest::getId).reversed())
                    .toList();
        } else if (actor.role() == Role.BP) {
            // 회사 단위 조회 — 같은 BP 회사 직원이 만든 요청을 모두 볼 수 있어야 함.
            if (actor.companyId() == null) return List.of();
            rows = repo.findByRequesterCompanyIdOrderByIdDesc(actor.companyId());
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() == null) return List.of();
            // 받은 요청(target) + 부모로서 자식에게 보낸 요청(requester 회사) 합집합, id 로 dedupe.
            Map<Long, DocumentSupplementRequest> byId = new java.util.LinkedHashMap<>();
            for (DocumentSupplementRequest r : repo.findByTargetSupplierCompanyIdOrderByIdDesc(actor.companyId())) byId.put(r.getId(), r);
            for (DocumentSupplementRequest r : repo.findByRequesterCompanyIdOrderByIdDesc(actor.companyId())) byId.put(r.getId(), r);
            rows = byId.values().stream()
                    .sorted(Comparator.comparing(DocumentSupplementRequest::getId).reversed())
                    .toList();
        } else {
            return List.of();
        }
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SupplementResponse get(Long id, AuthenticatedUser actor) {
        DocumentSupplementRequest r = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("SUPPLEMENT_NOT_FOUND",
                        "보완 요청 " + id + " 없음"));
        ensureCanView(actor, r);
        return toResponse(r);
    }

    public SupplementResponse cancel(Long id, AuthenticatedUser actor) {
        DocumentSupplementRequest r = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("SUPPLEMENT_NOT_FOUND",
                        "보완 요청 " + id + " 없음"));
        // 요청자와 같은 회사(BP 또는 부모 공급사) 또는 ADMIN 만 취소.
        if (actor.role() != Role.ADMIN && !isSameBpCompanyRequester(actor, r)) {
            throw ApiException.forbidden("CANCEL_DENIED", "취소 권한 없음");
        }
        if (r.getStatus() != DocumentSupplementStatus.OPEN) {
            throw ApiException.badRequest("ALREADY_CLOSED", "이미 처리된 요청입니다");
        }
        r.markCancelled();
        return toResponse(r);
    }

    /**
     * DocumentService.upload 후크 — 같은 자원/타입의 OPEN 보완 요청을 자동 RESOLVED.
     * (upload 시 호출. 트랜잭션 내. 실패해도 upload 자체에 영향 없도록 호출 측에서 try/catch.)
     */
    public int onDocumentUploaded(OwnerType ownerType, Long ownerId, Long documentTypeId, Long newDocId) {
        List<DocumentSupplementRequest> opens = repo
                .findByTargetOwnerTypeAndTargetOwnerIdAndDocumentTypeIdAndStatus(
                        ownerType, ownerId, documentTypeId, DocumentSupplementStatus.OPEN);
        Long verifierUserId = null;
        for (DocumentSupplementRequest r : opens) {
            r.markResolved(newDocId);
            if (verifierUserId == null) verifierUserId = r.getRequesterUserId();
            if (r.getRequesterUserId() != null) {
                notifications.sendToUser(r.getRequesterUserId(),
                        NotificationType.SUPPLEMENT_RESOLVED,
                        "보완 요청 처리 완료",
                        "공급사가 새 서류를 업로드해 보완 요청 #" + r.getId() + " 가 자동 처리됐습니다.",
                        "DOCUMENT_SUPPLEMENT", r.getId(), r.getContextSiteId());
            }
        }
        // BP 가 명시적으로 요청한 서류 → 공급사 회신 = 묵시적 승인. Document 도 VERIFIED 처리.
        if (!opens.isEmpty()) {
            final Long finalVerifier = verifierUserId;
            docRepo.findById(newDocId).ifPresent(d -> {
                if (finalVerifier != null) d.markVerifiedBy(finalVerifier);
                else d.markVerified();
                docRepo.save(d);
            });
        }
        return opens.size();
    }

    // ──────────────────────────────────────────────────────────────────

    private Long resolveSupplierCompanyId(OwnerType ownerType, Long ownerId) {
        if (ownerType == OwnerType.EQUIPMENT) {
            Equipment e = equipmentRepo.findById(ownerId).orElseThrow(() ->
                    ApiException.badRequest("EQUIPMENT_NOT_FOUND", "장비 없음"));
            return e.getSupplierId();
        }
        if (ownerType == OwnerType.PERSON) {
            Person p = personRepo.findById(ownerId).orElseThrow(() ->
                    ApiException.badRequest("PERSON_NOT_FOUND", "인원 없음"));
            return p.getSupplierId();
        }
        // COMPANY — owner 자체가 공급사 회사
        return ownerId;
    }

    private void ensureCanView(AuthenticatedUser actor, DocumentSupplementRequest r) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP && isSameBpCompanyRequester(actor, r)) return;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && r.getTargetSupplierCompanyId().equals(actor.companyId())) return;
        // 부모 공급사: 자식에게 보낸(요청자=본인 회사) 요청 열람 허용.
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && isSameBpCompanyRequester(actor, r)) return;
        throw ApiException.forbidden("VIEW_DENIED", "조회 권한 없음");
    }

    /** 요청자가 actor 와 같은 BP 회사 소속인지. 회사 메인 + 직원 계정 둘 다 같은 보완 요청 보이도록. */
    private boolean isSameBpCompanyRequester(AuthenticatedUser actor, DocumentSupplementRequest r) {
        if (actor.companyId() == null || r.getRequesterUserId() == null) return false;
        Long requesterCompanyId = users.findById(r.getRequesterUserId())
                .map(User::getCompanyId).orElse(null);
        return actor.companyId().equals(requesterCompanyId);
    }

    private SupplementResponse toResponse(DocumentSupplementRequest r) {
        DocumentType t = typeRepo.findById(r.getDocumentTypeId()).orElse(null);
        Company supplier = companies.findById(r.getTargetSupplierCompanyId()).orElse(null);
        String reqUserName = users.findById(r.getRequesterUserId()).map(User::getName).orElse(null);
        String siteName = r.getContextSiteId() != null
                ? sites.findById(r.getContextSiteId()).map(Site::getName).orElse(null)
                : null;
        String ownerName = ownerName(r.getTargetOwnerType(), r.getTargetOwnerId());

        return new SupplementResponse(
                r.getId(), r.getRequesterUserId(), reqUserName, r.getRequesterRole(),
                r.getTargetSupplierCompanyId(), supplier != null ? supplier.getName() : null,
                r.getTargetOwnerType(), r.getTargetOwnerId(), ownerName,
                r.getDocumentTypeId(), t != null ? t.getName() : null,
                r.getContextSiteId(), siteName, r.getContextWorkPlanId(),
                r.getReason(), r.getStatus(), r.getResolvedDocId(),
                r.getResolvedAt(), r.getCancelledAt(), r.getCreatedAt()
        );
    }

    private String ownerName(OwnerType type, Long id) {
        if (type == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(id).map(e ->
                    e.getVehicleNo() != null ? e.getVehicleNo()
                            : (e.getModel() != null ? e.getModel() : "장비#" + e.getId())).orElse("(?)");
        }
        if (type == OwnerType.PERSON) {
            return personRepo.findById(id).map(Person::getName).orElse("(?)");
        }
        return companies.findById(id).map(Company::getName).orElse("(?)");
    }
}
