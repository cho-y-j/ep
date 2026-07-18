package com.skep.resourcechange;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.readiness.DeployCheckResponse;
import com.skep.readiness.DeployCheckService;
import com.skep.resourcechange.dto.CreateResourceChangeRequest;
import com.skep.resourcechange.dto.ResourceChangeRequestResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업체변경 신청서 v0 (L2a, §3.6·§7). 생성 시 라벨/차량번호/조종원명/연락처 스냅샷 + 신규자원 L3(deploy-check) 자동 실행 저장.
 * 신청 주체: 공급사(본인 회사) · BP(대상 공급사 지정) · ADMIN.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ResourceChangeRequestService {

    private final ResourceChangeRequestRepository repo;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final DeployCheckService deployCheck;
    private final AuditLogService auditLog;

    public ResourceChangeRequestResponse create(CreateResourceChangeRequest req, AuthenticatedUser actor) {
        Role role = actor.role();
        boolean isSupplier = role == Role.EQUIPMENT_SUPPLIER || role == Role.MANPOWER_SUPPLIER;
        if (!isSupplier && role != Role.BP && role != Role.ADMIN) {
            throw ApiException.forbidden("DENIED", "공급사/BP/관리자만 업체변경 신청서를 작성할 수 있습니다");
        }

        Long supplierId;
        Long bpId;
        if (isSupplier) {
            supplierId = actor.companyId();
            if (supplierId == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
            bpId = req.bpCompanyId();
        } else if (role == Role.BP) {
            bpId = actor.companyId();
            supplierId = req.supplierCompanyId();
            if (supplierId == null) throw ApiException.badRequest("SUPPLIER_REQUIRED", "대상 공급사를 지정하세요");
        } else { // ADMIN
            bpId = req.bpCompanyId();
            supplierId = req.supplierCompanyId();
            if (supplierId == null) throw ApiException.badRequest("SUPPLIER_REQUIRED", "대상 공급사를 지정하세요");
        }
        if (bpId != null) validateBp(bpId);

        ResourceChangeRequest r = new ResourceChangeRequest();
        r.setChangeKind(req.changeKind());
        r.setSupplierCompanyId(supplierId);
        r.setBpCompanyId(bpId);
        r.setBpName(companyName(bpId));
        r.setSiteId(req.siteId());
        r.setSiteName(resolveSiteName(req.siteId(), req.siteName()));
        r.setReason(trimToNull(req.reason()));
        r.setApplyDate(req.applyDate());
        r.setWorkPlanId(req.workPlanId());
        r.setCreatedBy(actor.id());

        // 변경 전/후 자원 스냅샷 — 표시용 라벨·차량번호·조종원명·연락처.
        r.setOldEquipmentId(req.oldEquipmentId());
        r.setNewEquipmentId(req.newEquipmentId());
        r.setOldPersonId(req.oldPersonId());
        r.setNewPersonId(req.newPersonId());
        snapshotEquipment(req.oldEquipmentId(), r::setOldLabel, r::setOldVehicleNo);
        snapshotEquipment(req.newEquipmentId(), r::setNewLabel, r::setNewVehicleNo);
        snapshotPerson(req.oldPersonId(), r::setOldOperatorName, r::setOldContact, r::setOldLabel);
        snapshotPerson(req.newPersonId(), r::setNewOperatorName, r::setNewContact, r::setNewLabel);
        // 업체 변경 시 라벨 폴백 = 공급사명(자원 라벨이 없을 때).
        if (req.changeKind() == ResourceChangeKind.COMPANY) {
            if (r.getNewLabel() == null) r.setNewLabel(companyName(supplierId));
        }

        // 신규자원 L3(deploy-check) 자동 실행 — 장비 우선, 없으면 인원. 접근권한은 자원 스코프 준수(DeployCheckService).
        if (req.newEquipmentId() != null) {
            r.setL3Snapshot(toSnapshot(deployCheck.check("equipment", req.newEquipmentId(), req.siteId(), actor)));
        } else if (req.newPersonId() != null) {
            r.setL3Snapshot(toSnapshot(deployCheck.check("person", req.newPersonId(), req.siteId(), actor)));
        }

        repo.save(r);
        auditLog.record(actor, AuditAction.RESOURCE_CHANGE_REQUEST_CREATED, AuditTargetType.RESOURCE_CHANGE_REQUEST,
                r.getId(), supplierId, r.getSiteId(), null,
                "{\"change_kind\":\"" + r.getChangeKind().name() + "\"}");
        return toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<ResourceChangeRequestResponse> list(AuthenticatedUser actor) {
        List<ResourceChangeRequest> rows;
        if (actor.role() == Role.ADMIN) {
            rows = repo.findAllByOrderByIdDesc();
        } else if (actor.role() == Role.BP) {
            if (actor.companyId() == null) return List.of();
            rows = repo.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        } else {
            if (actor.companyId() == null) return List.of();
            rows = repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        }
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ResourceChangeRequestResponse get(Long id, AuthenticatedUser actor) {
        ResourceChangeRequest r = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CHANGE_REQUEST_NOT_FOUND", "업체변경 신청서를 찾을 수 없습니다"));
        boolean allowed = actor.role() == Role.ADMIN
                || (actor.companyId() != null && actor.companyId().equals(r.getSupplierCompanyId()))
                || (actor.companyId() != null && actor.companyId().equals(r.getBpCompanyId()));
        if (!allowed) throw ApiException.forbidden("DENIED", "이 신청서에 접근할 수 없습니다");
        return toResponse(r);
    }

    // ── helpers ─────────────────────────────────────────────────

    private void snapshotEquipment(Long id, java.util.function.Consumer<String> setLabel,
                                   java.util.function.Consumer<String> setVehicleNo) {
        if (id == null) return;
        Equipment e = equipmentRepo.findById(id).orElse(null);
        if (e == null) return;
        String label = e.getVehicleNo() != null ? e.getVehicleNo()
                : (e.getModel() != null ? e.getModel() : "장비 #" + id);
        setLabel.accept(label);
        setVehicleNo.accept(e.getVehicleNo());
    }

    private void snapshotPerson(Long id, java.util.function.Consumer<String> setName,
                                java.util.function.Consumer<String> setContact,
                                java.util.function.Consumer<String> setLabel) {
        if (id == null) return;
        Person p = personRepo.findById(id).orElse(null);
        if (p == null) return;
        setName.accept(p.getName());
        setContact.accept(p.getPhone());
        setLabel.accept(p.getName());
    }

    private Map<String, Object> toSnapshot(DeployCheckResponse dc) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("ready", dc.ready());
        snap.put("blocks", dc.blocks().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", b.kind());
            m.put("label", b.label());
            m.put("detail", b.detail());
            return m;
        }).toList());
        snap.put("checkedAt", LocalDate.now().toString());
        return snap;
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

    private ResourceChangeRequestResponse toResponse(ResourceChangeRequest r) {
        return ResourceChangeRequestResponse.from(r, companyName(r.getSupplierCompanyId()));
    }

    private String companyName(Long id) {
        if (id == null) return null;
        return companies.findById(id).map(Company::getName).orElse(null);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
