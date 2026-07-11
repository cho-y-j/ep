package com.skep.dashboard;

import com.skep.common.ApiException;
import com.skep.compliance.ComplianceService;
import com.skep.fieldDeployment.FieldDeploymentRepository;
import com.skep.fieldDeployment.FieldDeploymentRequest;
import com.skep.fieldDeployment.FieldDeploymentStatus;
import com.skep.resourceCheck.ResourceCheckRequest;
import com.skep.resourceCheck.ResourceCheckRequestRepository;
import com.skep.resourceCheck.ResourceCheckStatus;
import com.skep.safety.InspectionStatus;
import com.skep.safety.SafetyInspection;
import com.skep.safety.SafetyInspectionRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.signature.SignatureStatus;
import com.skep.signature.WorksheetSignatureRepository;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import com.skep.workplan.WorkPlan;
import com.skep.workplan.WorkPlanRepository;
import com.skep.workplan.WorkPlanStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * B3: BP 현장별 파이프라인(관제 대시보드). BP 가 이미 접근 가능한 원천만 현장 단위로 집계하는 읽기전용 서비스.
 * 배치조회로 N+1 회피(서류요약만 forSite 를 현장별 호출 — 권한(ensureCanReadSite) 내장 재사용).
 * 격리: 모든 원천을 actor 의 bp_company_id / 본인 현장으로 좁힌다.
 */
@Service
@RequiredArgsConstructor
public class BpSitePipelineService {

    private final SiteRepository sites;
    private final ResourceCheckRequestRepository resourceChecks;
    private final SafetyInspectionRepository safetyInspections;
    private final FieldDeploymentRepository fieldDeployments;
    private final WorkPlanRepository workPlans;
    private final WorksheetSignatureRepository signatures;
    private final ComplianceService complianceService;

    private static final int REQUIRED_SIGNATURES = 5;

    @Transactional(readOnly = true)
    public Map<String, Object> sitePipeline(AuthenticatedUser actor) {
        if (actor.role() != Role.BP) throw ApiException.forbidden("BP_ONLY", "BP 전용");
        if (actor.companyId() == null) throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        Long bpId = actor.companyId();

        List<Site> mySites = sites.findByBpCompanyIdOrderByIdDesc(bpId);
        List<Long> siteIds = mySites.stream().map(Site::getId).toList();
        Set<Long> siteIdSet = new HashSet<>(siteIds);

        // 1) 점검 — BP 발송분. 점검은 workPlanId 만 가지므로 workPlan → siteId 로 배치 매핑 후 현장별 집계.
        List<ResourceCheckRequest> checks = resourceChecks.findByBpCompanyIdOrderByIdDesc(bpId);
        List<Long> checkWpIds = checks.stream().map(ResourceCheckRequest::getWorkPlanId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> wpToSite = new HashMap<>();
        for (WorkPlan wp : workPlans.findAllById(checkWpIds)) wpToSite.put(wp.getId(), wp.getSiteId());
        Map<Long, long[]> checkBySite = new HashMap<>();   // [pending, approved]
        for (ResourceCheckRequest c : checks) {
            Long site = c.getWorkPlanId() != null ? wpToSite.get(c.getWorkPlanId()) : null;
            if (site == null || !siteIdSet.contains(site)) continue;
            long[] agg = checkBySite.computeIfAbsent(site, k -> new long[2]);
            if (c.getStatus() == ResourceCheckStatus.APPROVED) agg[1]++;
            else if (c.getStatus() != ResourceCheckStatus.CANCELLED) agg[0]++;
        }

        // 2) 검사일정 — 본인 현장 전체 배치 조회 후 현장별 집계.
        Map<Long, long[]> inspectionBySite = new HashMap<>();   // [scheduled, completed]
        if (!siteIds.isEmpty()) {
            for (SafetyInspection s : safetyInspections.findBySiteIdIn(siteIds)) {
                long[] agg = inspectionBySite.computeIfAbsent(s.getSiteId(), k -> new long[2]);
                if (s.getStatus() == InspectionStatus.COMPLETED) agg[1]++;
                else if (s.getStatus() != InspectionStatus.CANCELLED) agg[0]++;
            }
        }

        // 3) 투입요청 — BP 수신분. targetSiteId 별.
        Map<Long, long[]> deploymentBySite = new HashMap<>();   // [requested, active]
        for (FieldDeploymentRequest d : fieldDeployments.findByBpCompanyIdOrderByIdDesc(bpId)) {
            Long site = d.getTargetSiteId();
            if (site == null || !siteIdSet.contains(site)) continue;
            long[] agg = deploymentBySite.computeIfAbsent(site, k -> new long[2]);
            if (d.getStatus() == FieldDeploymentStatus.REQUESTED) agg[0]++;
            else if (d.getStatus() == FieldDeploymentStatus.ACTIVE) agg[1]++;
        }

        // 4) 서명대기 — DRAFT 작업계획서 중 SIGNED < 5(RoleDashboardController.bpPendingSignatures 로직 동일). 현장별.
        List<WorkPlan> drafts = workPlans.findByBpCompanyIdAndStatusInOrderByIdDesc(
                bpId, List.of(WorkPlanStatus.DRAFT));
        Map<Long, Long> signedCount = new HashMap<>();
        if (!drafts.isEmpty()) {
            List<Long> draftIds = drafts.stream().map(WorkPlan::getId).toList();
            for (Object[] row : signatures.countByStatusGroupedByWorkPlan(draftIds, SignatureStatus.SIGNED)) {
                signedCount.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, Long> pendingSignaturesBySite = new HashMap<>();
        for (WorkPlan wp : drafts) {
            if (signedCount.getOrDefault(wp.getId(), 0L) >= REQUIRED_SIGNATURES) continue;
            if (wp.getSiteId() == null || !siteIdSet.contains(wp.getSiteId())) continue;
            pendingSignaturesBySite.merge(wp.getSiteId(), 1L, Long::sum);
        }

        // 5) 조립 — 서류요약은 forSite(권한 내장) 현장별 호출.
        List<Map<String, Object>> siteRows = new ArrayList<>();
        for (Site site : mySites) {
            Long sid = site.getId();
            long[] ck = checkBySite.getOrDefault(sid, new long[2]);
            long[] insp = inspectionBySite.getOrDefault(sid, new long[2]);
            long[] dep = deploymentBySite.getOrDefault(sid, new long[2]);
            var compliance = complianceService.forSite(sid, actor);

            Map<String, Object> row = new HashMap<>();
            row.put("site_id", sid);
            row.put("site_name", site.getName());
            row.put("status", site.getStatus().name());
            row.put("resource_checks", Map.of("pending", ck[0], "approved", ck[1]));
            row.put("safety_inspections", Map.of("scheduled", insp[0], "completed", insp[1]));
            row.put("field_deployments", Map.of("requested", dep[0], "active", dep[1]));
            row.put("pending_signatures", pendingSignaturesBySite.getOrDefault(sid, 0L));
            row.put("documents", Map.of(
                    "progress_pct", compliance.progressPct(),
                    "ready", compliance.readyForWorkPlan()));
            siteRows.add(row);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("sites", siteRows);
        return body;
    }
}
