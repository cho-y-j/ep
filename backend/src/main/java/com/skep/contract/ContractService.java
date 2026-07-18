package com.skep.contract;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.contract.dto.ContractResponse;
import com.skep.contract.dto.SaveContractRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.storage.FileStorage;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractService {

    /** 계약서 첨부 허용 content-type — DocumentService.ALLOWED_CONTENT_TYPES 와 동일 계열. */
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "application/pdf",
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp",
            "image/heic", "image/heif",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final ContractRepository repo;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final FileStorage storage;
    private final AuditLogService auditLog;

    public ContractResponse create(SaveContractRequest req, AuthenticatedUser actor) {
        Long supplierId = requireSupplier(actor);
        Contract c = Contract.create(supplierId, actor.id());
        apply(c, req);
        repo.save(c);
        auditLog.record(actor, AuditAction.CONTRACT_CREATED, AuditTargetType.CONTRACT,
                c.getId(), supplierId, c.getSiteId(), null,
                "{\"rate_type\":\"" + c.getRateType().name() + "\"}");
        return toResponse(c);
    }

    public ContractResponse update(Long id, SaveContractRequest req, AuthenticatedUser actor) {
        Contract c = getForWrite(id, actor);
        apply(c, req);
        auditLog.record(actor, AuditAction.CONTRACT_UPDATED, AuditTargetType.CONTRACT,
                c.getId(), c.getSupplierCompanyId(), c.getSiteId(), null,
                "{\"rate_type\":\"" + c.getRateType().name() + "\"}");
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<ContractResponse> list(AuthenticatedUser actor) {
        List<Contract> rows;
        if (actor.role() == Role.ADMIN) {
            rows = repo.findAll().stream()
                    .sorted(Comparator.comparingLong(Contract::getId).reversed()).toList();
        } else if (actor.role() == Role.BP) {
            rows = actor.companyId() == null ? List.of()
                    : repo.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            rows = actor.companyId() == null ? List.of()
                    : repo.findBySupplierCompanyIdOrderByIdDesc(actor.companyId());
        } else {
            rows = List.of();
        }
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse get(Long id, AuthenticatedUser actor) {
        return toResponse(getForRead(id, actor));
    }

    public ContractResponse uploadFile(Long id, MultipartFile file, AuthenticatedUser actor) {
        Contract c = getForWrite(id, actor);
        String ct = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_FILE_TYPES.contains(ct)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE",
                    "지원하지 않는 파일 형식입니다 (PDF / 이미지 / Word / Excel 만 가능)");
        }
        String oldKey = c.getFileKey();
        String key = storage.store(file);
        c.setFileKey(key);
        c.touch();
        if (oldKey != null) storage.delete(oldKey);
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public Resource loadFile(Long id, AuthenticatedUser actor) {
        Contract c = getForRead(id, actor);
        if (c.getFileKey() == null) {
            throw ApiException.notFound("NO_FILE", "첨부된 계약서가 없습니다");
        }
        return storage.load(c.getFileKey());
    }

    // ── helpers ──────────────────────────────────────────────

    private Long requireSupplier(AuthenticatedUser actor) {
        if (actor.role() != Role.EQUIPMENT_SUPPLIER && actor.role() != Role.MANPOWER_SUPPLIER) {
            throw ApiException.forbidden("SUPPLIER_ONLY", "공급사만 계약을 등록할 수 있습니다");
        }
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        return actor.companyId();
    }

    private void apply(Contract c, SaveContractRequest req) {
        if (req.bpCompanyId() != null) {
            Company bp = companies.findById(req.bpCompanyId()).orElseThrow(() ->
                    ApiException.badRequest("BP_NOT_FOUND", "선택한 BP사를 찾을 수 없습니다"));
            if (bp.getType() != CompanyType.BP) {
                throw ApiException.badRequest("NOT_BP_COMPANY", "BP사가 아닌 회사는 선택할 수 없습니다");
            }
        }
        c.setBpCompanyId(req.bpCompanyId());
        c.setBpName(trimToNull(req.bpName()));
        c.setSiteId(req.siteId());
        // 현장 지정 시 이름 자동 채움, 미지정이면 텍스트 폴백.
        if (req.siteId() != null) {
            c.setSiteName(sites.findById(req.siteId()).map(Site::getName).orElse(trimToNull(req.siteName())));
        } else {
            c.setSiteName(trimToNull(req.siteName()));
        }
        c.setTitle(trimToNull(req.title()));
        c.setEquipmentDesc(trimToNull(req.equipmentDesc()));
        c.setRateType(req.rateType());
        c.setBaseRate(req.baseRate());
        c.setRateEarly(req.rateEarly());
        c.setRateLunch(req.rateLunch());
        c.setRateEvening(req.rateEvening());
        c.setRateNight(req.rateNight());
        c.setRateOvernight(req.rateOvernight());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setMemo(trimToNull(req.memo()));
        c.touch();
    }

    private Contract getForWrite(Long id, AuthenticatedUser actor) {
        Contract c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CONTRACT_NOT_FOUND", "계약을 찾을 수 없습니다"));
        if (actor.role() == Role.ADMIN) return c;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && c.getSupplierCompanyId().equals(actor.companyId())) return c;
        throw ApiException.forbidden("DENIED", "본인 회사 계약만 수정할 수 있습니다");
    }

    private Contract getForRead(Long id, AuthenticatedUser actor) {
        Contract c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CONTRACT_NOT_FOUND", "계약을 찾을 수 없습니다"));
        if (actor.role() == Role.ADMIN) return c;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && c.getSupplierCompanyId().equals(actor.companyId())) return c;
        if (actor.role() == Role.BP && actor.companyId() != null
                && actor.companyId().equals(c.getBpCompanyId())) return c;
        throw ApiException.forbidden("DENIED", "조회 권한이 없습니다");
    }

    private ContractResponse toResponse(Contract c) {
        String supplierName = companyName(c.getSupplierCompanyId());
        String bpCompanyName = c.getBpCompanyId() != null ? companyName(c.getBpCompanyId()) : null;
        return ContractResponse.from(c, supplierName, bpCompanyName);
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
