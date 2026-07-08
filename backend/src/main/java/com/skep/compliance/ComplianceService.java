package com.skep.compliance;

import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.compliance.dto.ComplianceItem;
import com.skep.compliance.dto.ResourceCompliance;
import com.skep.compliance.dto.SiteCompliance;
import com.skep.document.Document;
import com.skep.document.DocumentRepository;
import com.skep.document.DocumentType;
import com.skep.document.DocumentTypeRepository;
import com.skep.document.OwnerType;
import com.skep.document.VerificationStatus;
import com.skep.equipment.Equipment;
import com.skep.equipment.EquipmentCategory;
import com.skep.equipment.EquipmentRepository;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.person.PersonRole;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipant;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteParticipantType;
import com.skep.site.SiteRepository;
import com.skep.supplement.DocumentSupplementRequestRepository;
import com.skep.supplement.DocumentSupplementStatus;
import com.skep.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * S-11: 자원 (회사/장비/인원) 의 서류 컴플라이언스 평가 서비스.
 *
 * 핵심:
 *   1. document_types 의 (applies_to, applies_to_categories, applies_to_person_roles) 매칭으로 "필요 서류 catalog" 산출
 *   2. 자원의 chain head 서류 (latest by document_type_id) 평가 — VERIFIED/REJECTED/만료/누락
 *   3. OPEN 보완 요청 표시 — UI 에서 중복 요청 방지
 *
 * 게이트 정책: BP 회사 사업자등록증 chain head VERIFIED 일 때만 작업계획서 생성 가능 (별도 isBpDocsReady).
 */
@Service
@Transactional(readOnly = true)
public class ComplianceService {

    private static final int EXPIRING_DAYS = 30;
    private static final String BIZ_CERT_NAME = "사업자 등록증";

    private final DocumentTypeRepository typeRepo;
    private final DocumentRepository docRepo;
    private final EquipmentRepository equipmentRepo;
    private final PersonRepository personRepo;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final DocumentSupplementRequestRepository supplements;

    public ComplianceService(DocumentTypeRepository typeRepo, DocumentRepository docRepo,
                              EquipmentRepository equipmentRepo, PersonRepository personRepo,
                              CompanyRepository companies, SiteRepository sites,
                              SiteParticipantRepository participants,
                              DocumentSupplementRequestRepository supplements) {
        this.typeRepo = typeRepo;
        this.docRepo = docRepo;
        this.equipmentRepo = equipmentRepo;
        this.personRepo = personRepo;
        this.companies = companies;
        this.sites = sites;
        this.participants = participants;
        this.supplements = supplements;
    }

    // ──────────────────────────────────────────────────────────────────
    // 단일 자원 컴플라이언스
    // ──────────────────────────────────────────────────────────────────

    public ResourceCompliance forCompany(Long companyId, AuthenticatedUser actor) {
        Company c = companies.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("COMPANY_NOT_FOUND", "회사 " + companyId + " 없음"));
        return evaluate(OwnerType.COMPANY, c.getId(), c.getName(), c.getType().name(),
                c.getId(), c.getName(), null, null);
    }

    public ResourceCompliance forEquipment(Long equipmentId, AuthenticatedUser actor) {
        Equipment e = equipmentRepo.findById(equipmentId)
                .orElseThrow(() -> ApiException.notFound("EQUIPMENT_NOT_FOUND", "장비 " + equipmentId + " 없음"));
        Company supplier = companies.findById(e.getSupplierId()).orElse(null);
        String supplierName = supplier != null ? supplier.getName() : null;
        return evaluate(OwnerType.EQUIPMENT, e.getId(),
                e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "장비#" + e.getId()),
                e.getCategory() != null ? e.getCategory().name() : null,
                e.getSupplierId(), supplierName, e.getCategory(), null);
    }

    public ResourceCompliance forPerson(Long personId, AuthenticatedUser actor) {
        Person p = personRepo.findById(personId)
                .orElseThrow(() -> ApiException.notFound("PERSON_NOT_FOUND", "인원 " + personId + " 없음"));
        Company supplier = companies.findById(p.getSupplierId()).orElse(null);
        String supplierName = supplier != null ? supplier.getName() : null;
        Set<PersonRole> roles = p.getRoles();
        String rolesLabel = roles == null || roles.isEmpty() ? "" :
                roles.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
        return evaluate(OwnerType.PERSON, p.getId(), p.getName(), rolesLabel,
                p.getSupplierId(), supplierName, null, roles);
    }

    // ──────────────────────────────────────────────────────────────────
    // 사이트 통합 컴플라이언스
    // ──────────────────────────────────────────────────────────────────

    public SiteCompliance forSite(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "사이트 " + siteId + " 없음"));
        ensureCanReadSite(actor, site);

        // BP 회사 서류
        Company bp = companies.findById(site.getBpCompanyId()).orElse(null);
        String bpName = bp != null ? bp.getName() : "회사#" + site.getBpCompanyId();
        ResourceCompliance bpCompliance = evaluate(OwnerType.COMPANY,
                site.getBpCompanyId(), bpName, "BP",
                site.getBpCompanyId(), bpName, null, null);

        // 사이트 ACTIVE 참여 공급사들
        List<SiteParticipant> active = participants.findBySiteIdOrderByIdDesc(siteId).stream()
                .filter(p -> p.getStatus() == SiteParticipantStatus.ACTIVE)
                .toList();
        List<Long> equipmentSupplierIds = active.stream()
                .filter(p -> p.getParticipantType() == SiteParticipantType.EQUIPMENT_SUPPLIER)
                .map(SiteParticipant::getCompanyId).distinct().toList();
        List<Long> manpowerSupplierIds = active.stream()
                .filter(p -> p.getParticipantType() == SiteParticipantType.MANPOWER_SUPPLIER)
                .map(SiteParticipant::getCompanyId).distinct().toList();

        // 공급사 view 제한: 자기 회사 자원만 노출. 같은 사이트에 들어온 다른 공급사 자원·서류 노출 차단.
        boolean isSupplierView = actor.role() != com.skep.user.Role.ADMIN
                && actor.role() != com.skep.user.Role.BP;
        if (isSupplierView && actor.companyId() != null) {
            equipmentSupplierIds = equipmentSupplierIds.stream()
                    .filter(id -> id.equals(actor.companyId())).toList();
            manpowerSupplierIds = manpowerSupplierIds.stream()
                    .filter(id -> id.equals(actor.companyId())).toList();
        }

        // 자원 목록
        List<Equipment> equipments = equipmentSupplierIds.isEmpty() ? List.of()
                : equipmentRepo.findBySupplierIdInOrderByIdDesc(equipmentSupplierIds);
        List<Person> persons = manpowerSupplierIds.isEmpty() ? List.of()
                : personRepo.findBySupplierIdInOrderByIdDesc(manpowerSupplierIds);

        List<ResourceCompliance> equipmentComps = equipments.stream().map(e -> {
            Company sup = companies.findById(e.getSupplierId()).orElse(null);
            return evaluate(OwnerType.EQUIPMENT, e.getId(),
                    e.getVehicleNo() != null ? e.getVehicleNo() : (e.getModel() != null ? e.getModel() : "장비#" + e.getId()),
                    e.getCategory() != null ? e.getCategory().name() : null,
                    e.getSupplierId(), sup != null ? sup.getName() : null,
                    e.getCategory(), null);
        }).toList();

        List<ResourceCompliance> personComps = persons.stream().map(p -> {
            Company sup = companies.findById(p.getSupplierId()).orElse(null);
            String rolesLabel = p.getRoles() == null || p.getRoles().isEmpty() ? "" :
                    p.getRoles().stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
            return evaluate(OwnerType.PERSON, p.getId(), p.getName(), rolesLabel,
                    p.getSupplierId(), sup != null ? sup.getName() : null,
                    null, p.getRoles());
        }).toList();

        // 통합 진행률
        int totalReq = bpCompliance.requiredTotal()
                + equipmentComps.stream().mapToInt(ResourceCompliance::requiredTotal).sum()
                + personComps.stream().mapToInt(ResourceCompliance::requiredTotal).sum();
        int totalOk = bpCompliance.requiredOk()
                + equipmentComps.stream().mapToInt(ResourceCompliance::requiredOk).sum()
                + personComps.stream().mapToInt(ResourceCompliance::requiredOk).sum();
        int pct = totalReq == 0 ? 100 : (int) Math.round((totalOk * 100.0) / totalReq);
        boolean ready = totalReq == totalOk
                && bpCompliance.readyForWorkPlan()
                && equipmentComps.stream().allMatch(ResourceCompliance::readyForWorkPlan)
                && personComps.stream().allMatch(ResourceCompliance::readyForWorkPlan);

        return new SiteCompliance(
                site.getId(), site.getName(),
                site.getBpCompanyId(), bpName,
                bpCompliance, equipmentComps, personComps,
                totalReq, totalOk, pct, ready
        );
    }

    /** 게이트: BP 회사가 사업자등록증 VERIFIED 인가? */
    public boolean isBpBizCertVerified(Long bpCompanyId) {
        return !docRepo.findChainHeadByOwnerAndTypeName(
                OwnerType.COMPANY, bpCompanyId, BIZ_CERT_NAME, VerificationStatus.VERIFIED).isEmpty();
    }

    /** 게이트: 공급사(장비/인력) 회사가 사업자등록증 VERIFIED 인가? — 작업계획서 자원 추가 시 사용. */
    public boolean isSupplierBizCertVerified(Long supplierCompanyId) {
        return !docRepo.findChainHeadByOwnerAndTypeName(
                OwnerType.COMPANY, supplierCompanyId, BIZ_CERT_NAME, VerificationStatus.VERIFIED).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────
    // 핵심 evaluate 로직 — 단일 자원의 컴플라이언스 표 만들기
    // ──────────────────────────────────────────────────────────────────

    private ResourceCompliance evaluate(OwnerType ownerType, Long ownerId, String ownerName, String subLabel,
                                          Long supplierId, String supplierName,
                                          EquipmentCategory category, Set<PersonRole> roles) {
        // 1. 적용되는 document_types
        List<DocumentType> applicable = typeRepo.findByAppliesToAndActiveOrderBySortOrderAsc(ownerType, true).stream()
                .filter(t -> matches(t, ownerType, category, roles))
                .toList();

        // 2. 자원의 chain head 서류 (각 type 별 최신)
        Map<Long, Document> headByType = new HashMap<>();
        for (Document d : docRepo.findByOwnerTypeAndOwnerIdOrderByIdDesc(ownerType, ownerId)) {
            headByType.putIfAbsent(d.getDocumentTypeId(), d); // 가장 최신
        }

        // 3. OPEN 보완 요청 set — 자원 단위 일괄 조회.
        Set<Long> openSupplementTypes = new HashSet<>();
        for (var s : supplements.findByTargetOwnerTypeAndTargetOwnerIdAndStatus(
                ownerType, ownerId, DocumentSupplementStatus.OPEN)) {
            openSupplementTypes.add(s.getDocumentTypeId());
        }

        LocalDate today = LocalDate.now();
        List<ComplianceItem> items = new ArrayList<>();
        int requiredTotal = 0, requiredOk = 0, missing = 0, rejected = 0, expiring = 0, openSup = 0;

        for (DocumentType t : applicable) {
            Document head = headByType.get(t.getId());
            boolean present = head != null;
            boolean verified = present && head.getVerificationStatus() == VerificationStatus.VERIFIED;
            boolean isRejected = present && head.getVerificationStatus() == VerificationStatus.REJECTED;
            boolean ocrRev = present && head.getVerificationStatus() == VerificationStatus.OCR_REVIEW_REQUIRED;
            boolean expired = false, expiringSoon = false;
            String expDateStr = null;
            if (present && head.getExpiryDate() != null) {
                long days = ChronoUnit.DAYS.between(today, head.getExpiryDate());
                expired = days < 0;
                expiringSoon = !expired && days <= EXPIRING_DAYS;
                expDateStr = head.getExpiryDate().toString();
            }
            boolean openSupplement = openSupplementTypes.contains(t.getId());

            // OK 판정: required 일 때만 점수에 반영. 검증 + 미만료 + 보완요청 없음.
            boolean ok = verified && !expired && !openSupplement;
            if (t.isRequired()) {
                requiredTotal++;
                if (ok) requiredOk++;
                if (!present) missing++;
                if (isRejected) rejected++;
                if (expiringSoon || expired) expiring++;
            }
            if (openSupplement) openSup++;

            items.add(new ComplianceItem(
                    t.getId(), t.getName(), t.isRequired(), t.isBlocksAssignment(), t.isHasExpiry(),
                    present, verified, isRejected, ocrRev, expired, expiringSoon,
                    head != null ? head.getId() : null, expDateStr,
                    openSupplement
            ));
        }

        boolean ready = requiredTotal > 0 && requiredOk == requiredTotal && rejected == 0;
        // requiredTotal == 0 (필수 서류 없음) 이면 ready = true (vacuously)
        if (requiredTotal == 0) ready = true;

        return new ResourceCompliance(
                ownerType, ownerId, ownerName, subLabel,
                supplierId, supplierName,
                items,
                requiredTotal, requiredOk, missing, rejected, expiring, openSup, ready
        );
    }

    /** document_type 의 카테고리/역할 매핑이 자원과 매치하는가? NULL = 모든 sub-type 매치. */
    private static boolean matches(DocumentType t, OwnerType ownerType,
                                    EquipmentCategory category, Set<PersonRole> roles) {
        if (ownerType == OwnerType.EQUIPMENT) {
            String csv = t.getAppliesToCategories();
            if (csv == null || csv.isBlank()) return true;
            if (category == null) return false;
            for (String s : csv.split(",")) {
                if (s.trim().equalsIgnoreCase(category.name())) return true;
            }
            return false;
        }
        if (ownerType == OwnerType.PERSON) {
            String csv = t.getAppliesToPersonRoles();
            if (csv == null || csv.isBlank()) return true;
            if (roles == null || roles.isEmpty()) return false;
            for (String s : csv.split(",")) {
                String trimmed = s.trim();
                for (PersonRole r : roles) {
                    if (r.name().equalsIgnoreCase(trimmed)) return true;
                }
            }
            return false;
        }
        // COMPANY 는 sub-type 없음 — 항상 매치
        return true;
    }

    private void ensureCanReadSite(AuthenticatedUser actor, Site site) {
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP) {
            if (actor.companyId() != null && site.getBpCompanyId().equals(actor.companyId())) return;
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            if (actor.companyId() != null) {
                boolean hasMembership = participants.findBySiteIdOrderByIdDesc(site.getId()).stream()
                        .anyMatch(p -> p.getStatus() == SiteParticipantStatus.ACTIVE
                                && Objects.equals(p.getCompanyId(), actor.companyId()));
                if (hasMembership) return;
            }
        }
        throw ApiException.forbidden("COMPLIANCE_DENIED", "사이트 컴플라이언스 조회 권한이 없습니다");
    }
}
