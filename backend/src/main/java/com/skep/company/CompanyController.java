package com.skep.company;

import com.skep.common.ApiException;
import com.skep.company.dto.CompanyResponse;
import com.skep.company.dto.CreateChildRequest;
import com.skep.company.dto.CreateCompanyRequest;
import com.skep.company.dto.UpdateCompanyRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;
    private final com.skep.user.CompanyUserService companyUsers;

    public CompanyController(CompanyService service, com.skep.user.CompanyUserService companyUsers) {
        this.service = service;
        this.companyUsers = companyUsers;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<CompanyResponse> list(@RequestParam(required = false) CompanyType type) {
        List<Company> result = type == null ? service.listAll() : service.listByType(type);
        return result.stream().map(CompanyResponse::from).toList();
    }

    /** 공급사도 BP 사 목록은 조회 가능 — 정식 견적서 발송 수신처 선택용. ADMIN/BP/공급사 전용. */
    @GetMapping("/bp-list")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<CompanyResponse> bpList() {
        return service.listByType(CompanyType.BP).stream().map(CompanyResponse::from).toList();
    }

    /** BP/공급사 모두 접근 가능한 공급사 목록 — WP 작성 '전체 보기 토글' 시 사용. */
    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<CompanyResponse> suppliers(@RequestParam("type") CompanyType type) {
        if (type != CompanyType.EQUIPMENT && type != CompanyType.MANPOWER) {
            return List.of();
        }
        return service.listByType(type).stream().map(CompanyResponse::from).toList();
    }

    /**
     * BP 가 견적으로 연동(선정/사인)한 공급사 목록.
     * - quotation_request_targets FINAL_ACCEPTED (TARGETED)
     * - quotation_proposals FINAL_ACCEPTED (OPEN_BID)
     * - outgoing_quotations bp_signed (영업 견적 수락)
     * type=EQUIPMENT|MANPOWER 로 필터.
     */
    @GetMapping("/connected-suppliers")
    public List<CompanyResponse> connectedSuppliers(@RequestParam("type") CompanyType type,
                                                     @com.skep.security.CurrentUser com.skep.security.AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw com.skep.common.ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 가능합니다");
        }
        Long bpId = actor.role() == Role.ADMIN ? null : actor.companyId();
        return service.connectedSuppliers(bpId, type).stream().map(CompanyResponse::from).toList();
    }

    /** BP 가 특정 공급사로부터 견적/사인 연동한 자원 id 목록 (장비 or 인원). */
    @GetMapping("/connected-resources")
    public java.util.Map<String, java.util.List<Long>> connectedResources(
            @RequestParam("supplierId") Long supplierId,
            @com.skep.security.CurrentUser com.skep.security.AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw com.skep.common.ApiException.forbidden("BP_ADMIN_ONLY", "BP/ADMIN 만 가능합니다");
        }
        Long bpId = actor.role() == Role.ADMIN ? null : actor.companyId();
        return service.connectedResources(bpId, supplierId);
    }

    /** 공급사가 특정 BP사의 사용자(담당자) 목록을 조회 — 정식 견적서 수신자 선택용. ADMIN/BP/공급사 전용. */
    @GetMapping("/{id}/bp-users")
    @PreAuthorize("hasAnyRole('ADMIN','BP','EQUIPMENT_SUPPLIER','MANPOWER_SUPPLIER')")
    public List<com.skep.user.dto.UserResponse> bpUsers(@PathVariable Long id) {
        return service.usersByCompany(id).stream()
                .filter(u -> u.role() == Role.BP)
                .toList();
    }

    /** V77: 하위(자식) 공급사 등록. EQUIPMENT_SUPPLIER master 는 본인 자식, ADMIN 은 body parentCompanyId 지정. */
    @PostMapping("/children")
    @PreAuthorize("hasAnyRole('ADMIN','EQUIPMENT_SUPPLIER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createChild(@Valid @RequestBody CreateChildRequest req,
                                       @CurrentUser AuthenticatedUser actor) {
        return CompanyResponse.from(service.createChild(actor, req));
    }

    /** V77: 직속 자식 공급사 목록. EQUIPMENT_SUPPLIER=본인 자식, ADMIN=쿼리 parentId. */
    @GetMapping("/children")
    @PreAuthorize("hasAnyRole('ADMIN','EQUIPMENT_SUPPLIER')")
    public List<CompanyResponse> children(@RequestParam(required = false) Long parentId,
                                          @CurrentUser AuthenticatedUser actor) {
        Long pid = actor.role() == Role.ADMIN ? parentId : actor.companyId();
        return service.listChildren(pid).stream().map(CompanyResponse::from).toList();
    }

    /** V77: 직속 자식 공급사의 가입 유저 목록 — 부모 master 가 가입 대기 승인 대상 확인. */
    @GetMapping("/children/{childId}/users")
    @PreAuthorize("hasRole('EQUIPMENT_SUPPLIER')")
    public List<com.skep.auth.dto.UserResponse> childUsers(@PathVariable Long childId,
                                                           @CurrentUser AuthenticatedUser actor) {
        return companyUsers.listChildUsers(childId, actor).stream()
                .map(com.skep.auth.dto.UserResponse::from).toList();
    }

    /** V77: 부모 master 가 직속 자식 공급사 가입 유저를 승인(활성화 + 자식회사 첫 유저 master 승격). */
    @PostMapping("/children/users/{id}/approve")
    @PreAuthorize("hasRole('EQUIPMENT_SUPPLIER')")
    public com.skep.auth.dto.UserResponse approveChildUser(@PathVariable Long id,
                                                           @CurrentUser AuthenticatedUser actor) {
        return com.skep.auth.dto.UserResponse.from(companyUsers.approveChildUser(id, actor));
    }

    /**
     * S-Audit P1-D: ADMIN 전체, 그 외 역할은 자기 회사만.
     */
    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        boolean isOwn = actor.companyId() != null && actor.companyId().equals(id);
        // V77: 부모(장비공급사 master)는 직속 자식(협력사) 회사정보도 조회 가능.
        if (actor.role() != Role.ADMIN && !isOwn && !isParentMasterOf(actor, id)) {
            throw ApiException.forbidden("COMPANY_VIEW_DENIED", "본인 회사 또는 하위 공급사만 조회할 수 있습니다");
        }
        return CompanyResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CreateCompanyRequest req) {
        return CompanyResponse.from(service.create(req.name(), req.businessNumber(), req.type()));
    }

    /** 회사명 변경. ADMIN 또는 상위(부모) 공급사 master 가 직속 자식 회사명 대행 변경. 사업자번호는 변경 대상 아님. */
    @PatchMapping("/{id}")
    public CompanyResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCompanyRequest req,
                                  @CurrentUser AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && !isParentMasterOf(actor, id)) {
            throw ApiException.forbidden("RENAME_DENIED", "관리자 또는 상위 공급사 마스터만 회사명을 변경할 수 있습니다");
        }
        return CompanyResponse.from(service.rename(id, req.name()));
    }

    /** 회사 프로필(견적서 양식용) 편집. ADMIN 또는 해당 회사의 isCompanyAdmin 가 본인 회사만 가능. */
    @PatchMapping("/{id}/profile")
    public CompanyResponse updateProfile(@PathVariable Long id,
                                         @Valid @RequestBody com.skep.company.dto.UpdateCompanyProfileRequest req,
                                         @CurrentUser AuthenticatedUser actor) {
        boolean isAdmin = actor.role() == Role.ADMIN;
        boolean isOwnCompanyAdmin = actor.isCompanyAdmin()
                && actor.companyId() != null && actor.companyId().equals(id);
        // V77: 부모(장비공급사 master)는 직속 자식(협력사) 프로필도 대행 편집 가능.
        if (!isAdmin && !isOwnCompanyAdmin && !isParentMasterOf(actor, id)) {
            throw ApiException.forbidden("PROFILE_EDIT_DENIED", "본인 회사 또는 하위 공급사의 마스터만 편집할 수 있습니다");
        }
        return CompanyResponse.from(service.updateProfile(id,
                req.businessAddress(), req.businessCategory(), req.businessSubcategory(),
                req.ceoName(), req.phone(), req.fax()));
    }

    /** V77: actor 가 회사 childId 의 부모(직속 상위) master 인지. 부모 master 만 자식 회사정보 대행. */
    private boolean isParentMasterOf(AuthenticatedUser actor, Long childId) {
        if (!actor.isCompanyAdmin() || actor.companyId() == null) return false;
        return service.listChildren(actor.companyId()).stream()
                .anyMatch(c -> c.getId().equals(childId));
    }

    /** V33+: 회사에 가입된 사용자(직원) 목록. ADMIN 전용. */
    @GetMapping("/{id}/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<com.skep.user.dto.UserResponse> usersByCompany(@PathVariable Long id) {
        return service.usersByCompany(id);
    }

    /** V33+: 견적 거래 이력 있는 연동 공급사 목록 (BP 회사 기준 distinct). ADMIN 전용. */
    @GetMapping("/{id}/partners")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CompanyResponse> partnersByBp(@PathVariable Long id) {
        return service.partnersByBp(id);
    }
}
