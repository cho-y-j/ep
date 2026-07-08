package com.skep.company;

import com.skep.common.ApiException;
import com.skep.company.dto.CompanyResponse;
import com.skep.user.UserRepository;
import com.skep.user.dto.UserResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository repo;
    private final UserRepository userRepo;

    @PersistenceContext
    private EntityManager em;

    public CompanyService(CompanyRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
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
