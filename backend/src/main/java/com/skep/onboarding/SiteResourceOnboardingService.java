package com.skep.onboarding;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.document.OwnerType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.equipment.EquipmentService;
import com.skep.notification.NotificationService;
import com.skep.onboarding.dto.CreateOnboardingRequest;
import com.skep.onboarding.dto.OnboardingResponse;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.person.PersonService;
import com.skep.resourceCheck.ResourceCheckRequest;
import com.skep.resourceCheck.ResourceCheckRequestRepository;
import com.skep.resourceCheck.ResourceCheckType;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 기통과 소급 + 구두승인(§3.8). 핵심 설계 결정: 게이트/readiness 로직은 무수정,
 * 확정 시 기존 ResourceCheck 승인행을 주입해 기존 판정을 통과시킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SiteResourceOnboardingService {

    private final SiteResourceOnboardingRepository repo;
    private final ResourceCheckRequestRepository resourceChecks;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final EquipmentService equipmentService;
    private final PersonService personService;
    private final NotificationService notifications;
    private final AuditLogService auditLog;

    /** 공급사가 기투입 자원 1건을 신고(REQUESTED) 또는 구두승인(VERBAL) 기록. */
    public OnboardingResponse create(CreateOnboardingRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 기투입 등록이 가능합니다");
        }
        Long supplierId = actor.companyId();
        if (supplierId == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        if (req.mode() == OnboardingMode.APPROVED) {
            throw ApiException.badRequest("INVALID_MODE", "승인은 BP 소급 승인으로만 처리됩니다");
        }
        validateOwnership(req.ownerType(), req.ownerId(), supplierId);

        String siteName = resolveSiteName(req.siteId(), req.siteName());

        if (req.mode() == OnboardingMode.REQUESTED) {
            if (req.bpCompanyId() == null) {
                throw ApiException.badRequest("BP_REQUIRED", "BP 승인 요청에는 BP사를 지정해야 합니다");
            }
            validateBp(req.bpCompanyId());
            SiteResourceOnboarding o = SiteResourceOnboarding.request(supplierId, req.ownerType(), req.ownerId(),
                    req.siteId(), siteName, req.bpCompanyId(),
                    req.inspectionDate(), req.educationDate(), req.healthDate(),
                    trimToNull(req.memo()), actor.id());
            repo.save(o);
            notifications.sendToCompany(req.bpCompanyId(),
                    "RESOURCE_ONBOARDING_REQUESTED", "기투입 소급 승인 요청 도착",
                    ownerLabel(o.getOwnerType(), o.getOwnerId()) + " — 소급 승인 대기",
                    "RESOURCE_ONBOARDING", o.getId(), o.getSiteId(), notifications.senderLabelOf(actor));
            auditLog.record(actor, AuditAction.RESOURCE_ONBOARDING_REQUESTED, AuditTargetType.RESOURCE_ONBOARDING,
                    o.getId(), supplierId, o.getSiteId(), null, ownerJson(o));
            return toResponse(o);
        }

        // VERBAL — 공급사 구두승인 기록, 즉시 확정.
        if (req.bpCompanyId() != null) validateBp(req.bpCompanyId());
        SiteResourceOnboarding o = SiteResourceOnboarding.verbal(supplierId, req.ownerType(), req.ownerId(),
                req.siteId(), siteName, req.bpCompanyId(),
                req.inspectionDate(), req.educationDate(), req.healthDate(),
                trimToNull(req.verbalApprover()), trimToNull(req.memo()), actor.id());
        repo.save(o);
        issueApprovedChecks(o, actor);
        auditLog.record(actor, AuditAction.RESOURCE_ONBOARDING_VERBAL, AuditTargetType.RESOURCE_ONBOARDING,
                o.getId(), supplierId, o.getSiteId(), null, ownerJson(o));
        return toResponse(o);
    }

    /** BP 소급 승인 — REQUESTED → APPROVED, ResourceCheck 승인행 주입. */
    public OnboardingResponse approve(Long id, AuthenticatedUser actor) {
        if (actor.role() != Role.BP && actor.role() != Role.ADMIN) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 소급 승인할 수 있습니다");
        }
        SiteResourceOnboarding o = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("ONBOARDING_NOT_FOUND", "기투입 건을 찾을 수 없습니다"));
        if (actor.role() == Role.BP && !actor.companyId().equals(o.getBpCompanyId())) {
            throw ApiException.forbidden("DENIED", "본인 회사 앞 건만 승인할 수 있습니다");
        }
        if (o.getMode() != OnboardingMode.REQUESTED) {
            throw ApiException.badRequest("INVALID_STATE", "승인 대기 상태에서만 승인할 수 있습니다");
        }
        o.approve(actor.id());
        issueApprovedChecks(o, actor);
        notifications.sendToCompany(o.getSupplierCompanyId(),
                "RESOURCE_ONBOARDING_APPROVED", "기투입 소급 승인 완료",
                ownerLabel(o.getOwnerType(), o.getOwnerId()) + " — 투입 확정",
                "RESOURCE_ONBOARDING", o.getId(), o.getSiteId(), notifications.senderLabelOf(actor));
        auditLog.record(actor, AuditAction.RESOURCE_ONBOARDING_APPROVED, AuditTargetType.RESOURCE_ONBOARDING,
                o.getId(), o.getSupplierCompanyId(), o.getSiteId(), null, ownerJson(o));
        return toResponse(o);
    }

    @Transactional(readOnly = true)
    public List<OnboardingResponse> listForSupplier(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OnboardingResponse> listForBp(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    /** 자원 상세 배지용 — 이 자원의 확정 온보딩(소급 승인/구두승인).
     *  접근권한은 자원(장비/인원)의 기존 스코프 그대로(DeployCheckService 와 동일 · ADMIN 포함). */
    @Transactional(readOnly = true)
    public List<OnboardingResponse> listConfirmedForResource(OwnerType ownerType, Long ownerId, AuthenticatedUser actor) {
        if (ownerType == OwnerType.EQUIPMENT) {
            equipmentService.get(ownerId, actor);
        } else if (ownerType == OwnerType.PERSON) {
            personService.get(ownerId, actor);
        } else {
            throw ApiException.badRequest("BAD_TYPE", "장비/인원만 조회할 수 있습니다");
        }
        return repo.findByOwnerTypeAndOwnerIdAndModeInOrderByIdDesc(
                        ownerType, ownerId, List.of(OnboardingMode.APPROVED, OnboardingMode.VERBAL)).stream()
                .map(this::toResponse).toList();
    }

    // ── ResourceCheck 주입(핵심 훅) ─────────────────────────────

    /**
     * 확정 시 기존 ResourceCheck 승인행 자동 생성 — 장비=반입검사(VEHICLE_SAFETY),
     * 인원=건강검진(HEALTH_CHECK)+안전교육(SAFETY_TRAINING). 완료일이 있으면 비고에 표기.
     * bp_company_id 없는 VERBAL 건은 ResourceCheck.bp_company_id(NOT NULL) 구조상 생략 — onboarding 레코드만 확정.
     */
    private void issueApprovedChecks(SiteResourceOnboarding o, AuthenticatedUser actor) {
        if (o.getBpCompanyId() == null) return;
        String modeLabel = o.getMode() == OnboardingMode.VERBAL
                ? "구두승인" + (notBlank(o.getVerbalApprover()) ? "(" + o.getVerbalApprover() + ")" : "")
                : "소급 승인";
        if (o.getOwnerType() == OwnerType.EQUIPMENT) {
            issueCheck(o, ResourceCheckType.VEHICLE_SAFETY, o.getInspectionDate(), modeLabel, actor);
        } else {
            issueCheck(o, ResourceCheckType.HEALTH_CHECK, o.getHealthDate(), modeLabel, actor);
            issueCheck(o, ResourceCheckType.SAFETY_TRAINING, o.getEducationDate(), modeLabel, actor);
        }
    }

    private void issueCheck(SiteResourceOnboarding o, ResourceCheckType type,
                            LocalDate completedDate, String modeLabel, AuthenticatedUser actor) {
        String note = modeLabel + (completedDate != null ? " · 완료일 " + completedDate : "");
        ResourceCheckRequest rc = ResourceCheckRequest.issue(
                null, o.getOwnerType(), o.getOwnerId(),
                o.getSupplierCompanyId(), o.getBpCompanyId(),
                type, null, note, actor.id());
        rc.approve(actor.id(), note);
        resourceChecks.save(rc);
    }

    // ── helpers ────────────────────────────────────────────────

    private void validateOwnership(OwnerType type, Long ownerId, Long supplierId) {
        if (type == OwnerType.EQUIPMENT) {
            Equipment e = equipmentRepo.findById(ownerId).orElseThrow(() ->
                    ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비를 찾을 수 없습니다"));
            if (!supplierId.equals(e.getSupplierId())) {
                throw ApiException.forbidden("DENIED", "본인 회사 자원만 등록할 수 있습니다");
            }
        } else if (type == OwnerType.PERSON) {
            Person p = personRepo.findById(ownerId).orElseThrow(() ->
                    ApiException.notFound("PERSON_NOT_FOUND", "인원을 찾을 수 없습니다"));
            if (!supplierId.equals(p.getSupplierId())) {
                throw ApiException.forbidden("DENIED", "본인 회사 자원만 등록할 수 있습니다");
            }
        } else {
            throw ApiException.badRequest("BAD_TYPE", "장비/인원만 등록할 수 있습니다");
        }
    }

    private void validateBp(Long bpCompanyId) {
        Company bp = companies.findById(bpCompanyId).orElseThrow(() ->
                ApiException.badRequest("BP_NOT_FOUND", "선택한 BP사를 찾을 수 없습니다"));
        if (bp.getType() != CompanyType.BP) {
            throw ApiException.badRequest("NOT_BP_COMPANY", "BP사가 아닌 회사는 선택할 수 없습니다");
        }
    }

    private String resolveSiteName(Long siteId, String fallback) {
        if (siteId != null) {
            return sites.findById(siteId).map(Site::getName).orElse(trimToNull(fallback));
        }
        return trimToNull(fallback);
    }

    private OnboardingResponse toResponse(SiteResourceOnboarding o) {
        String supplierName = companyName(o.getSupplierCompanyId());
        String bpName = o.getBpCompanyId() != null ? companyName(o.getBpCompanyId()) : null;
        return OnboardingResponse.from(o, supplierName, bpName,
                ownerLabel(o.getOwnerType(), o.getOwnerId()));
    }

    private String companyName(Long id) {
        if (id == null) return null;
        return companies.findById(id).map(Company::getName).orElse(null);
    }

    private String ownerLabel(OwnerType type, Long id) {
        if (type == OwnerType.EQUIPMENT) {
            return equipmentRepo.findById(id)
                    .map(e -> e.getVehicleNo() != null ? e.getVehicleNo()
                            : (e.getModel() != null ? e.getModel() : "장비 #" + id))
                    .orElse("장비 #" + id);
        }
        if (type == OwnerType.PERSON) {
            return personRepo.findById(id).map(Person::getName).orElse("인원 #" + id);
        }
        return type.name() + " #" + id;
    }

    private String ownerJson(SiteResourceOnboarding o) {
        return "{\"owner_type\":\"" + o.getOwnerType().name() + "\",\"owner_id\":" + o.getOwnerId()
                + ",\"mode\":\"" + o.getMode().name() + "\"}";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
