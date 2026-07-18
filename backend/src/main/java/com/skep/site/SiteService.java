package com.skep.site;

import com.skep.audit.AuditAction;
import com.skep.audit.AuditLogService;
import com.skep.audit.AuditTargetType;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.company.CompanyType;
import com.skep.security.AuthenticatedUser;
import com.skep.site.dto.*;
import com.skep.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class SiteService {

    private final SiteRepository sites;
    private final SiteParticipantRepository participants;
    private final CompanyRepository companies;
    private final AuditLogService auditLog;

    public SiteService(SiteRepository sites, SiteParticipantRepository participants,
                       CompanyRepository companies, AuditLogService auditLog) {
        this.sites = sites;
        this.participants = participants;
        this.companies = companies;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public List<SiteResponse> list(AuthenticatedUser actor) {
        List<Site> siteList;
        if (actor.role() == Role.ADMIN) {
            siteList = sites.findAllByOrderByIdDesc();
        } else if (actor.role() == Role.BP) {
            requireCompany(actor);
            siteList = sites.findByBpCompanyIdOrderByIdDesc(actor.companyId());
        } else if (actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER) {
            requireCompany(actor);
            List<Long> siteIds = participants
                    .findByCompanyIdAndStatusOrderByIdDesc(actor.companyId(), SiteParticipantStatus.ACTIVE)
                    .stream()
                    .map(SiteParticipant::getSiteId)
                    .toList();
            siteList = siteIds.isEmpty()
                    ? List.of()
                    : sites.findAllById(siteIds).stream()
                    .sorted(Comparator.comparing(Site::getId).reversed())
                    .toList();
        } else {
            siteList = List.of();
        }

        Map<Long, Company> companyMap = companyMap(siteList.stream().map(Site::getBpCompanyId).toList());
        Map<Long, Integer> counts = participantCounts(siteList);
        return siteList.stream()
                .map(s -> SiteResponse.summary(s, companyMap.get(s.getBpCompanyId()), counts.getOrDefault(s.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SiteResponse get(Long id, AuthenticatedUser actor) {
        Site site = getSite(id);
        ensureCanView(actor, site);
        return detailResponse(site);
    }

    public SiteResponse create(CreateSiteRequest req, AuthenticatedUser actor) {
        Long bpCompanyId = resolveBpCompanyId(req.bpCompanyId(), actor);
        Company bpCompany = companies.findById(bpCompanyId)
                .orElseThrow(() -> ApiException.badRequest("BP_COMPANY_NOT_FOUND", "BP 회사를 찾을 수 없습니다"));
        if (bpCompany.getType() != CompanyType.BP) {
            throw ApiException.badRequest("COMPANY_NOT_BP", "현장은 BP 회사에만 생성할 수 있습니다");
        }

        Site siteToSave = Site.builder()
                .bpCompanyId(bpCompanyId)
                .clientOrgId(req.clientOrgId())
                .name(req.name())
                .code(blankToNull(req.code()))
                .address(blankToNull(req.address()))
                .detailAddress(blankToNull(req.detailAddress()))
                .startDate(req.startDate())
                .endDate(req.endDate())
                .status(SiteStatus.ACTIVE)
                .createdBy(actor.id())
                .build();
        siteToSave.updateMap(req.latitude(), req.longitude(), blankToNull(req.polygonGeojson()), req.mapZoom());
        Site site = sites.save(siteToSave);
        auditLog.record(actor, AuditAction.SITE_CREATED, AuditTargetType.SITE,
                site.getId(), bpCompanyId, site.getId(),
                null,
                "{\"name\":\"" + escape(site.getName()) + "\",\"status\":\"" + site.getStatus().name() + "\"}");
        return detailResponse(site);
    }

    public SiteResponse update(Long id, UpdateSiteRequest req, AuthenticatedUser actor) {
        Site site = getSite(id);
        ensureCanManage(actor, site);
        String beforeJson = "{\"name\":\"" + escape(site.getName()) + "\",\"status\":\"" + site.getStatus().name() + "\"}";
        site.update(
                req.name(),
                blankToNull(req.code()),
                blankToNull(req.address()),
                blankToNull(req.detailAddress()),
                req.startDate(),
                req.endDate(),
                req.status()
        );
        site.updateMap(req.latitude(), req.longitude(), blankToNull(req.polygonGeojson()), req.mapZoom());
        site.updateSettlementDay(req.settlementDay());
        site.updateClientOrg(req.clientOrgId());
        auditLog.record(actor, AuditAction.SITE_UPDATED, AuditTargetType.SITE,
                site.getId(), site.getBpCompanyId(), site.getId(),
                beforeJson,
                "{\"name\":\"" + escape(site.getName()) + "\",\"status\":\"" + site.getStatus().name() + "\"}");
        return detailResponse(site);
    }

    public SiteResponse addParticipant(Long siteId, AddSiteParticipantRequest req, AuthenticatedUser actor) {
        Site site = getSite(siteId);
        ensureCanManage(actor, site);
        Company company = companies.findById(req.companyId())
                .orElseThrow(() -> ApiException.badRequest("PARTICIPANT_COMPANY_NOT_FOUND", "참여 업체를 찾을 수 없습니다"));
        if (company.getType() != CompanyType.EQUIPMENT && company.getType() != CompanyType.MANPOWER) {
            throw ApiException.badRequest("PARTICIPANT_MUST_BE_SUPPLIER", "현장 참여 업체는 장비공급사 또는 인력공급사여야 합니다");
        }

        SiteParticipantType type = SiteParticipantType.fromCompanyType(company.getType());
        SiteParticipant participant = participants.findBySiteIdAndCompanyId(siteId, company.getId())
                .map(existing -> {
                    existing.activate();
                    return existing;
                })
                .orElseGet(() -> participants.save(SiteParticipant.builder()
                        .siteId(siteId)
                        .companyId(company.getId())
                        .participantType(type)
                        .status(SiteParticipantStatus.ACTIVE)
                        .addedBy(actor.id())
                        .build()));
        participant.activate();
        auditLog.record(actor, AuditAction.PARTICIPANT_ADDED, AuditTargetType.SITE_PARTICIPANT,
                participant.getId(), company.getId(), site.getId(),
                null,
                "{\"site_id\":" + site.getId() + ",\"company_id\":" + company.getId() + ",\"type\":\"" + type.name() + "\"}");
        return detailResponse(site);
    }

    public SiteResponse removeParticipant(Long siteId, Long participantId, AuthenticatedUser actor) {
        Site site = getSite(siteId);
        ensureCanManage(actor, site);
        SiteParticipant participant = participants.findById(participantId)
                .orElseThrow(() -> ApiException.notFound("PARTICIPANT_NOT_FOUND", "참여 업체를 찾을 수 없습니다"));
        if (!participant.getSiteId().equals(siteId)) {
            throw ApiException.badRequest("PARTICIPANT_SITE_MISMATCH", "현장 참여 업체 정보가 일치하지 않습니다");
        }
        participant.deactivate();
        auditLog.record(actor, AuditAction.PARTICIPANT_REMOVED, AuditTargetType.SITE_PARTICIPANT,
                participant.getId(), participant.getCompanyId(), site.getId(),
                "{\"status\":\"ACTIVE\"}",
                "{\"status\":\"INACTIVE\"}");
        return detailResponse(site);
    }

    @Transactional(readOnly = true)
    public List<Company> supplierCompanies(CompanyType type, AuthenticatedUser actor) {
        if (actor.role() != Role.ADMIN && actor.role() != Role.BP) {
            throw ApiException.forbidden("SUPPLIER_LOOKUP_FORBIDDEN", "공급사 목록 조회 권한이 없습니다");
        }
        if (type != CompanyType.EQUIPMENT && type != CompanyType.MANPOWER) {
            throw ApiException.badRequest("SUPPLIER_TYPE_REQUIRED", "type은 EQUIPMENT 또는 MANPOWER만 가능합니다");
        }
        return companies.findByType(type);
    }

    private SiteResponse detailResponse(Site site) {
        Company bpCompany = companies.findById(site.getBpCompanyId()).orElse(null);
        List<SiteParticipant> participantList = participants.findBySiteIdOrderByIdDesc(site.getId());
        Map<Long, Company> companyMap = companyMap(participantList.stream().map(SiteParticipant::getCompanyId).toList());
        List<SiteParticipantResponse> participantResponses = participantList.stream()
                .map(p -> SiteParticipantResponse.from(p, companyMap.get(p.getCompanyId())))
                .toList();
        return SiteResponse.detail(site, bpCompany, participantResponses);
    }

    private Site getSite(Long id) {
        return sites.findById(id)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
    }

    private Long resolveBpCompanyId(Long requestedBpCompanyId, AuthenticatedUser actor) {
        if (actor.role() == Role.ADMIN) {
            if (requestedBpCompanyId == null) {
                throw ApiException.badRequest("BP_COMPANY_REQUIRED", "ADMIN은 bp_company_id가 필요합니다");
            }
            return requestedBpCompanyId;
        }
        if (actor.role() == Role.BP) {
            requireCompany(actor);
            if (requestedBpCompanyId != null && !requestedBpCompanyId.equals(actor.companyId())) {
                throw ApiException.forbidden("FORBIDDEN_OTHER_BP_COMPANY", "다른 BP 회사의 현장을 생성할 수 없습니다");
            }
            return actor.companyId();
        }
        throw ApiException.forbidden("ROLE_NOT_ALLOWED", "현장 생성 권한이 없습니다");
    }

    private void ensureCanView(AuthenticatedUser actor, Site site) {
        if (actor.role() == Role.ADMIN) return;
        requireCompany(actor);
        if (actor.role() == Role.BP && site.getBpCompanyId().equals(actor.companyId())) return;
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && participants.existsBySiteIdAndCompanyIdAndStatus(site.getId(), actor.companyId(), SiteParticipantStatus.ACTIVE)) {
            return;
        }
        throw ApiException.forbidden("SITE_ACCESS_DENIED", "현장 접근 권한이 없습니다");
    }

    private void ensureCanManage(AuthenticatedUser actor, Site site) {
        if (actor.role() == Role.ADMIN) return;
        requireCompany(actor);
        if (actor.role() == Role.BP && site.getBpCompanyId().equals(actor.companyId())) return;
        throw ApiException.forbidden("SITE_MANAGE_DENIED", "현장 관리 권한이 없습니다");
    }

    private void requireCompany(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
        }
    }

    private Map<Long, Company> companyMap(Collection<Long> companyIds) {
        List<Long> ids = companyIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return companies.findAllById(ids).stream()
                .collect(Collectors.toMap(Company::getId, Function.identity()));
    }

    private Map<Long, Integer> participantCounts(List<Site> siteList) {
        List<Long> siteIds = siteList.stream().map(Site::getId).toList();
        if (siteIds.isEmpty()) return Map.of();
        Map<Long, Integer> counts = new HashMap<>();
        participants.findBySiteIdIn(siteIds).forEach(p -> {
            if (p.getStatus() == SiteParticipantStatus.ACTIVE) {
                counts.merge(p.getSiteId(), 1, Integer::sum);
            }
        });
        return counts;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    /** JSON 문자열 값에 들어갈 ", \, 줄바꿈을 escape. 단순 헬퍼. */
    private static String escape(String s) {
        return com.skep.common.SafeText.escapeJson(s);
    }
}
