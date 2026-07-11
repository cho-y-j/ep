package com.skep.safety;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.notification.NotificationService;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.sms.SmsService;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.safety.dto.CreateInspectionRequest;
import com.skep.safety.dto.InspectionResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SafetyInspectionService {

    private final SafetyInspectionRepository repo;
    private final SiteRepository sites;
    private final CompanyRepository companies;
    private final EquipmentRepository equipments;
    private final PersonRepository persons;
    private final NotificationService notifications;
    private final UserRepository users;
    private final SmsService smsService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d HH:mm");

    /** BP/ADMIN 만 일정 등록. */
    @Transactional
    public InspectionResponse create(CreateInspectionRequest req, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 일정 등록 가능합니다");
        }
        Site site = sites.findById(req.siteId())
                .orElseThrow(() -> ApiException.badRequest("SITE_NOT_FOUND", "현장 없음"));
        if (actor.role() == Role.BP && !site.getBpCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("NOT_YOUR_SITE", "본인 현장만 가능");
        }
        // 대상 검증 + supplier 자동 추출
        Long supplierId = req.supplierCompanyId();
        String targetLabel;
        if (req.targetType() == InspectionTarget.VEHICLE) {
            Equipment e = equipments.findById(req.targetId())
                    .orElseThrow(() -> ApiException.badRequest("TARGET_NOT_FOUND", "장비 없음"));
            if (supplierId == null) supplierId = e.getSupplierId();
            targetLabel = e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "#" + e.getId());
        } else {
            Person p = persons.findById(req.targetId())
                    .orElseThrow(() -> ApiException.badRequest("TARGET_NOT_FOUND", "인원 없음"));
            if (supplierId == null) supplierId = p.getSupplierId();
            targetLabel = p.getName();
        }

        SafetyInspection saved = repo.save(SafetyInspection.builder()
                .siteId(site.getId())
                .supplierCompanyId(supplierId)
                .targetType(req.targetType())
                .targetId(req.targetId())
                .kind(req.kind())
                .scheduledAt(req.scheduledAt())
                .durationMinutes(req.durationMinutes())
                .inspectorId(req.inspectorId())
                .createdBy(actor.id())
                .build());

        String supplierName = supplierId != null
                ? companies.findById(supplierId).map(Company::getName).orElse(null) : null;
        return InspectionResponse.from(saved, site.getName(), supplierName, targetLabel);
    }

    /** S-3.1: BP 가 등록한 일정을 공급사에 통보. 1회 멱등. */
    @Transactional
    public InspectionResponse sendToSupplier(Long inspectionId, AuthenticatedUser actor) {
        SafetyInspection s = repo.findById(inspectionId)
                .orElseThrow(() -> ApiException.notFound("INSPECTION_NOT_FOUND", "검사 일정 없음"));
        ensureCanManage(s, actor);
        s.markSent();  // 이미 SENT 면 IllegalStateException → 멱등성 보장

        if (s.getSupplierCompanyId() != null) {
            String kindLabel = s.getKind() == InspectionKind.VEHICLE_INSPECTION ? "차량검사" : "입소검사";
            String when = s.getScheduledAt().format(DATE_FMT);
            String message = kindLabel + " 일정이 " + when + " 로 등록되었습니다. 확인 후 사인해주세요.";
            notifications.sendToCompany(s.getSupplierCompanyId(),
                    "SAFETY_INSPECTION",
                    "[" + kindLabel + "] 일정 통보",
                    message,
                    "SAFETY_INSPECTION", s.getId(), s.getSiteId());
            // SMS — 공급사 마스터 사용자 phone 으로 (SKEP_SMS_ENABLED=false 면 log 만).
            for (User u : users.findByCompanyIdAndIsCompanyAdminTrue(s.getSupplierCompanyId())) {
                if (u.getPhone() != null && !u.getPhone().isBlank()) {
                    smsService.send(u.getPhone(),
                            "[안전점검] " + kindLabel + " " + when + " — 확인 바랍니다.",
                            "SAFETY_INSPECTION", actor.id());
                }
            }
        }
        return toResponse(s);
    }

    /** 공급사가 일정 확인. */
    @Transactional
    public InspectionResponse confirm(Long inspectionId, AuthenticatedUser actor) {
        SafetyInspection s = repo.findById(inspectionId)
                .orElseThrow(() -> ApiException.notFound("INSPECTION_NOT_FOUND", "검사 일정 없음"));
        if (actor.role() != Role.ADMIN
                && (s.getSupplierCompanyId() == null || !s.getSupplierCompanyId().equals(actor.companyId()))) {
            throw ApiException.forbidden("NOT_PERMITTED", "공급사만 확인 가능");
        }
        s.markConfirmed();
        return toResponse(s);
    }

    /** BP/ADMIN 검사 완료 처리. */
    @Transactional
    public InspectionResponse complete(Long inspectionId, String resultNotes, AuthenticatedUser actor) {
        SafetyInspection s = repo.findById(inspectionId)
                .orElseThrow(() -> ApiException.notFound("INSPECTION_NOT_FOUND", "검사 일정 없음"));
        ensureCanManage(s, actor);
        s.markCompleted(resultNotes);
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> listBySite(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장 없음"));
        if (actor.role() == Role.ADMIN) {
            // pass
        } else if (actor.role() == Role.BP) {
            if (!java.util.Objects.equals(site.getBpCompanyId(), actor.companyId())) {
                throw ApiException.forbidden("NOT_PERMITTED", "본인 현장만");
            }
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            // 공급사는 자기 회사가 참여 중인 현장만. 별도 endpoint /mine 사용 권장이지만
            // 여기서는 차단 (자기 회사 자원 검사만 보려면 /mine).
            throw ApiException.forbidden("USE_MINE_ENDPOINT", "공급사는 /api/safety-inspections/mine 사용");
        } else {
            throw ApiException.forbidden("NOT_PERMITTED", "권한 없음");
        }
        return repo.findBySiteIdOrderByScheduledAtAsc(siteId).stream()
                .map(this::toResponse).toList();
    }

    /** 공급사 받은 검사 일정 list. */
    @Transactional(readOnly = true)
    public List<InspectionResponse> listMine(AuthenticatedUser actor) {
        if (actor.companyId() == null) return List.of();
        return repo.findBySupplierCompanyIdOrderByScheduledAtAsc(actor.companyId()).stream()
                .map(this::toResponse).toList();
    }

    /** G-1 게이트용: 자원별 COMPLETED 여부 빠른 조회. */
    @Transactional(readOnly = true)
    public boolean isCompleted(Long siteId, InspectionTarget type, Long targetId) {
        return repo.findBySiteIdAndTargetTypeAndTargetId(siteId, type, targetId).stream()
                .anyMatch(s -> s.getStatus() == InspectionStatus.COMPLETED);
    }

    /**
     * B5: target(장비/인원) 기준 검사 상태 조회 — BP 본인 현장 대상만. (A2 연결뷰에서 점검목록에 상태 병기용)
     * BP 는 자기 현장의 검사만, ADMIN 은 전체. 공급사는 /mine 사용.
     */
    @Transactional(readOnly = true)
    public List<InspectionResponse> listByTargetForBp(InspectionTarget targetType, Long targetId, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("NOT_PERMITTED", "BP/ADMIN 전용");
        }
        List<SafetyInspection> found = repo.findByTargetTypeAndTargetIdIn(targetType, List.of(targetId));
        if (actor.role() == Role.ADMIN) {
            return found.stream().map(this::toResponse).toList();
        }
        if (actor.companyId() == null) return List.of();
        java.util.Set<Long> mySiteIds = sites.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream()
                .map(Site::getId).collect(Collectors.toSet());
        return found.stream()
                .filter(s -> mySiteIds.contains(s.getSiteId()))
                .map(this::toResponse).toList();
    }

    private void ensureCanManage(SafetyInspection s, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) return;
        Site site = sites.findById(s.getSiteId()).orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장 없음"));
        if (actor.role() == Role.BP && site.getBpCompanyId().equals(actor.companyId())) return;
        throw ApiException.forbidden("NOT_PERMITTED", "권한 없음");
    }

    private InspectionResponse toResponse(SafetyInspection s) {
        String siteName = sites.findById(s.getSiteId()).map(Site::getName).orElse(null);
        String supplierName = s.getSupplierCompanyId() != null
                ? companies.findById(s.getSupplierCompanyId()).map(Company::getName).orElse(null) : null;
        String targetLabel;
        if (s.getTargetType() == InspectionTarget.VEHICLE) {
            targetLabel = equipments.findById(s.getTargetId())
                    .map(e -> e.getVehicleNo() != null ? e.getVehicleNo()
                            : (e.getModel() != null ? e.getModel() : "#" + e.getId()))
                    .orElse("#" + s.getTargetId());
        } else {
            targetLabel = persons.findById(s.getTargetId()).map(Person::getName).orElse("#" + s.getTargetId());
        }
        return InspectionResponse.from(s, siteName, supplierName, targetLabel);
    }
}
