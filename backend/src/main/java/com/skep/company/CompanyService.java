package com.skep.company;

import com.skep.common.ApiException;
import com.skep.company.dto.CompanyResponse;
import com.skep.company.dto.CreateChildRequest;
import com.skep.security.AuthenticatedUser;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import com.skep.user.dto.UserResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository repo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager em;

    public CompanyService(CompanyRepository repo, UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * V77 보안 불변식 1: 읽기 스코프 = 자식이면 {본인}, 부모면 {본인} ∪ {직속 자식}. 1단계만(재귀 없음).
     * 부모를 가진 회사는 자식 생성이 차단되므로 findByParentCompanyId 는 항상 비어 있어 자연히 {본인} 이 된다.
     */
    @Transactional(readOnly = true)
    public List<Long> selfAndChildren(Long companyId) {
        if (companyId == null) return List.of();
        List<Long> ids = new ArrayList<>();
        ids.add(companyId);
        for (Company child : repo.findByParentCompanyId(companyId)) {
            ids.add(child.getId());
        }
        return ids;
    }

    /** V77: 부모의 직속 자식 목록(1단계). */
    @Transactional(readOnly = true)
    public List<Company> listChildren(Long parentCompanyId) {
        if (parentCompanyId == null) return List.of();
        return repo.findByParentCompanyId(parentCompanyId);
    }

    /**
     * V77: 부모(EQUIPMENT 공급사 master)가 하위 공급사(자식)를 등록.
     * - ADMIN: body 의 parentCompanyId 지정. 그 외 EQUIPMENT_SUPPLIER: 본인이 부모(master 필수).
     * - 부모는 EQUIPMENT type, 그리고 스스로 자식이 아니어야 함(1단계 강제).
     * - 자식 type ∈ {EQUIPMENT, MANPOWER}.
     * - admin 계정(email/password/name) 3개 모두 있으면 자식 회사 master 계정 함께 생성.
     */
    public Company createChild(AuthenticatedUser actor, CreateChildRequest req) {
        Long parentId;
        if (actor.role() == Role.ADMIN) {
            if (req.parentCompanyId() == null) {
                throw ApiException.badRequest("PARENT_REQUIRED", "부모 회사 id 가 필요합니다 (ADMIN)");
            }
            parentId = req.parentCompanyId();
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER) {
            if (actor.companyId() == null) {
                throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
            }
            if (!actor.isCompanyAdmin()) {
                throw ApiException.forbidden("NOT_COMPANY_ADMIN", "회사 관리자만 하위 공급사를 등록할 수 있습니다");
            }
            parentId = actor.companyId();
        } else {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "하위 공급사 등록 권한이 없는 역할입니다");
        }

        Company parent = get(parentId);
        if (parent.getType() != CompanyType.EQUIPMENT) {
            throw ApiException.forbidden("PARENT_NOT_EQUIPMENT", "장비공급사만 하위 공급사를 둘 수 있습니다");
        }
        if (parent.getParentCompanyId() != null) {
            throw ApiException.forbidden("PARENT_IS_CHILD", "이미 하위 공급사인 회사는 다시 하위를 둘 수 없습니다 (1단계만 허용)");
        }
        if (req.type() != CompanyType.EQUIPMENT && req.type() != CompanyType.MANPOWER) {
            throw ApiException.badRequest("CHILD_TYPE_INVALID", "자식 유형은 EQUIPMENT 또는 MANPOWER 만 가능합니다");
        }
        if (repo.existsByBusinessNumber(req.businessNumber())) {
            throw ApiException.conflict("BUSINESS_NUMBER_EXISTS", "이미 등록된 사업자번호입니다");
        }

        Company child = Company.builder()
                .name(req.name())
                .businessNumber(req.businessNumber())
                .type(req.type())
                .build();
        child.assignParent(parentId);
        repo.save(child);

        boolean hasEmail = req.adminEmail() != null && !req.adminEmail().isBlank();
        if (hasEmail) {
            if (req.adminPassword() == null || req.adminPassword().isBlank()
                    || req.adminName() == null || req.adminName().isBlank()) {
                throw ApiException.badRequest("ADMIN_FIELDS_REQUIRED", "관리자 계정은 이메일/비밀번호/이름을 모두 입력하세요");
            }
            if (userRepo.existsByEmail(req.adminEmail())) {
                throw ApiException.conflict("EMAIL_EXISTS", "이미 사용 중인 이메일입니다");
            }
            Role childRole = req.type() == CompanyType.EQUIPMENT ? Role.EQUIPMENT_SUPPLIER : Role.MANPOWER_SUPPLIER;
            userRepo.save(User.builder()
                    .email(req.adminEmail())
                    .password(passwordEncoder.encode(req.adminPassword()))
                    .name(req.adminName())
                    .role(childRole)
                    .companyId(child.getId())
                    .isCompanyAdmin(true)
                    .enabled(true)
                    .build());
        }
        return child;
    }

    @Transactional(readOnly = true)
    public List<Company> listAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public List<Company> listByType(CompanyType type) {
        return repo.findByType(type);
    }

    /**
     * BP 가 견적/사인으로 연동된 공급사 목록 (distinct), 지정한 type 만.
     * - TARGETED 견적 FINAL_ACCEPTED target.supplier_company_id
     * - OPEN_BID 견적 FINAL_ACCEPTED proposal.supplier_company_id
     * - outgoing_quotations BP 사인 완료 supplier_company_id
     * bpCompanyId == null 이면 ADMIN 전체 자원 회사 (= listByType 과 동일).
     */
    @Transactional(readOnly = true)
    public List<Company> connectedSuppliers(Long bpCompanyId, CompanyType type) {
        if (bpCompanyId == null) return repo.findByType(type);
        String typeName = type.name();
        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery(
                "SELECT DISTINCT s.supplier_company_id FROM ( " +
                "  SELECT t.supplier_company_id FROM quotation_request_targets t " +
                "    JOIN quotation_requests qr ON qr.id = t.request_id " +
                "    WHERE qr.bp_company_id = :bpId AND t.status = 'FINAL_ACCEPTED' " +
                "  UNION " +
                "  SELECT p.supplier_company_id FROM quotation_proposals p " +
                "    JOIN quotation_requests qr ON qr.id = p.request_id " +
                "    WHERE qr.bp_company_id = :bpId AND p.status = 'FINAL_ACCEPTED' " +
                "  UNION " +
                "  SELECT o.supplier_company_id FROM outgoing_quotations o " +
                "    WHERE o.recipient_company_id = :bpId AND o.bp_signed_at IS NOT NULL " +
                ") s " +
                "JOIN companies c ON c.id = s.supplier_company_id " +
                "WHERE c.type = :type")
                .setParameter("bpId", bpCompanyId)
                .setParameter("type", typeName)
                .getResultList();
        if (ids.isEmpty()) return List.of();
        List<Long> longIds = ids.stream().map(Number::longValue).toList();
        return repo.findAllById(longIds);
    }

    /** BP 가 특정 supplier 로부터 finalize/사인 연동된 자원(equipment/person) id 집합. */
    @Transactional(readOnly = true)
    public java.util.Map<String, java.util.List<Long>> connectedResources(Long bpCompanyId, Long supplierCompanyId) {
        if (bpCompanyId == null) {
            // ADMIN: 그 공급사의 모든 자원 허용 (제약 없음). 빈 map → 프론트가 필터 안 함.
            return java.util.Map.of("equipmentIds", java.util.List.of(), "personIds", java.util.List.of());
        }
        @SuppressWarnings("unchecked")
        java.util.List<Number> eqRows = em.createNativeQuery(
                "SELECT DISTINCT eq_id FROM ( " +
                "  SELECT t.equipment_id AS eq_id FROM quotation_request_targets t " +
                "    JOIN quotation_requests qr ON qr.id = t.request_id " +
                "    WHERE qr.bp_company_id = :bp AND t.supplier_company_id = :sup " +
                "      AND t.status = 'FINAL_ACCEPTED' AND t.equipment_id IS NOT NULL " +
                "  UNION " +
                "  SELECT p.equipment_id AS eq_id FROM quotation_proposals p " +
                "    JOIN quotation_requests qr ON qr.id = p.request_id " +
                "    WHERE qr.bp_company_id = :bp AND p.supplier_company_id = :sup " +
                "      AND p.status = 'FINAL_ACCEPTED' AND p.equipment_id IS NOT NULL " +
                "  UNION " +
                "  SELECT o.equipment_id AS eq_id FROM outgoing_quotations o " +
                "    WHERE o.recipient_company_id = :bp AND o.supplier_company_id = :sup " +
                "      AND o.bp_signed_at IS NOT NULL AND o.equipment_id IS NOT NULL " +
                "  UNION " +
                "  SELECT d.equipment_id AS eq_id FROM quotation_dispatched_equipments d " +
                "    JOIN quotation_requests qr ON qr.id = d.quotation_request_id " +
                "    WHERE qr.bp_company_id = :bp AND d.supplier_company_id = :sup " +
                ") x WHERE eq_id IS NOT NULL")
                .setParameter("bp", bpCompanyId)
                .setParameter("sup", supplierCompanyId)
                .getResultList();
        @SuppressWarnings("unchecked")
        java.util.List<Number> ppRows = em.createNativeQuery(
                "SELECT DISTINCT p_id FROM ( " +
                "  SELECT t.person_id AS p_id FROM quotation_request_targets t " +
                "    JOIN quotation_requests qr ON qr.id = t.request_id " +
                "    WHERE qr.bp_company_id = :bp AND t.supplier_company_id = :sup " +
                "      AND t.status = 'FINAL_ACCEPTED' AND t.person_id IS NOT NULL " +
                "  UNION " +
                "  SELECT p.person_id AS p_id FROM quotation_proposals p " +
                "    JOIN quotation_requests qr ON qr.id = p.request_id " +
                "    WHERE qr.bp_company_id = :bp AND p.supplier_company_id = :sup " +
                "      AND p.status = 'FINAL_ACCEPTED' AND p.person_id IS NOT NULL " +
                "  UNION " +
                "  SELECT o.person_id AS p_id FROM outgoing_quotations o " +
                "    WHERE o.recipient_company_id = :bp AND o.supplier_company_id = :sup " +
                "      AND o.bp_signed_at IS NOT NULL AND o.person_id IS NOT NULL " +
                "  UNION " +
                "  SELECT d.person_id AS p_id FROM quotation_dispatched_persons d " +
                "    JOIN quotation_requests qr ON qr.id = d.quotation_request_id " +
                "    WHERE qr.bp_company_id = :bp AND d.supplier_company_id = :sup " +
                ") x WHERE p_id IS NOT NULL")
                .setParameter("bp", bpCompanyId)
                .setParameter("sup", supplierCompanyId)
                .getResultList();
        java.util.List<Long> eqIds = eqRows.stream().map(Number::longValue).toList();
        java.util.List<Long> ppIds = ppRows.stream().map(Number::longValue).toList();
        return java.util.Map.of("equipmentIds", eqIds, "personIds", ppIds);
    }

    @Transactional(readOnly = true)
    public Company get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("COMPANY_NOT_FOUND", "company " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Company> findByBusinessNumber(String businessNumber) {
        return repo.findByBusinessNumber(businessNumber);
    }

    public Company create(String name, String businessNumber, CompanyType type) {
        if (repo.existsByBusinessNumber(businessNumber)) {
            throw ApiException.conflict("BUSINESS_NUMBER_EXISTS", "이미 등록된 사업자번호입니다");
        }
        return repo.save(Company.builder()
                .name(name)
                .businessNumber(businessNumber)
                .type(type)
                .build());
    }

    public Company rename(Long id, String newName) {
        Company c = get(id);
        c.rename(newName);
        return c;
    }

    /** 견적서 양식에 채울 회사 프로필 필드 업데이트. */
    public Company updateProfile(Long id, String businessAddress, String businessCategory, String businessSubcategory,
                                 String ceoName, String phone, String fax) {
        Company c = get(id);
        c.updateProfile(businessAddress, businessCategory, businessSubcategory, ceoName, phone, fax);
        return c;
    }

    /**
     * Used during signup. Returns existing company if business number matches AND type matches.
     * Throws on type mismatch. Creates new if not found.
     */
    public CompanyResolution resolveOrCreate(String name, String businessNumber, CompanyType type) {
        Optional<Company> existing = repo.findByBusinessNumber(businessNumber);
        if (existing.isPresent()) {
            Company c = existing.get();
            if (c.getType() != type) {
                throw ApiException.conflict("COMPANY_TYPE_MISMATCH",
                        "이미 등록된 사업자번호이지만 회사 유형이 다릅니다");
            }
            return new CompanyResolution(c, false);
        }
        Company created = repo.save(Company.builder()
                .name(name)
                .businessNumber(businessNumber)
                .type(type)
                .build());
        return new CompanyResolution(created, true);
    }

    public record CompanyResolution(Company company, boolean isNew) {}

    /** V33+: 회사 가입 사용자(직원) 목록. */
    @Transactional(readOnly = true)
    public List<UserResponse> usersByCompany(Long companyId) {
        return userRepo.findByCompanyIdOrderByIdAsc(companyId).stream()
                .map(UserResponse::from).toList();
    }

    /**
     * V33+: BP 회사의 견적 거래 이력 있는 공급사 distinct.
     * 견적 거래 = (1) site_participants ACTIVE + (2) quotation_request_targets + (3) quotation_proposals.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<CompanyResponse> partnersByBp(Long bpId) {
        // JPQL — 3가지 거래 출처 합쳐 distinct supplier company.
        List<Company> partners = em.createQuery("""
                SELECT DISTINCT c FROM Company c
                WHERE c.id IN (
                    SELECT sp.companyId FROM com.skep.site.SiteParticipant sp
                    JOIN com.skep.site.Site s ON s.id = sp.siteId
                    WHERE s.bpCompanyId = :bp
                      AND sp.status = com.skep.site.SiteParticipantStatus.ACTIVE
                )
                OR c.id IN (
                    SELECT t.supplierCompanyId FROM com.skep.quotation.QuotationRequestTarget t
                    JOIN com.skep.quotation.QuotationRequest r ON r.id = t.requestId
                    WHERE r.onBehalfOfBpCompanyId = :bp
                       OR r.requestedByUserId IN (SELECT u.id FROM com.skep.user.User u WHERE u.companyId = :bp)
                )
                OR c.id IN (
                    SELECT p.supplierCompanyId FROM com.skep.quotation.proposal.QuotationProposal p
                    JOIN com.skep.quotation.QuotationRequest r ON r.id = p.requestId
                    WHERE r.onBehalfOfBpCompanyId = :bp
                       OR r.requestedByUserId IN (SELECT u.id FROM com.skep.user.User u WHERE u.companyId = :bp)
                )
                ORDER BY c.name ASC
                """, Company.class)
                .setParameter("bp", bpId)
                .getResultList();
        return partners.stream().map(CompanyResponse::from).toList();
    }
}
