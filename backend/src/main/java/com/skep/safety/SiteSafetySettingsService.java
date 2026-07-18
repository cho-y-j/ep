package com.skep.safety;

import com.skep.common.ApiException;
import com.skep.safety.dto.SiteSafetySettingsRequest;
import com.skep.safety.dto.SiteSafetySettingsResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현장 안전설정 조회/저장. BP(자기 현장)·ADMIN(전체) 접근.
 * 저장 시 SafetyThresholds.validateNotWeakerThanLegal() 로 법정 완화 금지 가드 강제.
 */
@Service
@RequiredArgsConstructor
public class SiteSafetySettingsService {

    private final SiteSafetySettingsRepository repo;
    private final SiteRepository sites;

    /** 현재 유효값(행 있으면 그 값, 없으면 법정 기본값). */
    @Transactional(readOnly = true)
    public SiteSafetySettingsResponse get(Long siteId, AuthenticatedUser actor) {
        ensureCanManage(siteId, actor);
        return repo.findBySiteId(siteId)
                .map(SiteSafetySettingsResponse::of)
                .orElseGet(() -> SiteSafetySettingsResponse.ofDefaults(siteId));
    }

    /** 저장(upsert) — 가드 통과 시에만. */
    @Transactional
    public SiteSafetySettingsResponse save(Long siteId, SiteSafetySettingsRequest req, AuthenticatedUser actor) {
        ensureCanManage(siteId, actor);

        SafetyThresholds def = SafetyThresholds.legalDefault();
        SafetyThresholds candidate = new SafetyThresholds(
                orDefault(req.tempCaution(), def.tempCaution()),
                orDefault(req.tempWarning(), def.tempWarning()),
                orDefault(req.tempDanger(), def.tempDanger()),
                orDefault(req.tempExtreme(), def.tempExtreme()),
                orDefault(req.restIntervalMin(), def.restIntervalMin()),
                orDefault(req.restDurationMin(), def.restDurationMin()),
                orDefault(req.middayStartHour(), def.middayStartHour()),
                orDefault(req.middayEndHour(), def.middayEndHour()),
                orDefault(req.windStopMps(), def.windStopMps()),
                req.enforceDailyInspectionGate() != null && req.enforceDailyInspectionGate(),
                req.maintenanceIntervalHours()); // null 허용 = 정비 알림 비활성
        candidate.validateNotWeakerThanLegal();

        SiteSafetySettings s = repo.findBySiteId(siteId).orElseGet(() -> SiteSafetySettings.defaults(siteId));
        s.apply(candidate.tempCaution(), candidate.tempWarning(), candidate.tempDanger(), candidate.tempExtreme(),
                candidate.restIntervalMin(), candidate.restDurationMin(),
                candidate.middayStartHour(), candidate.middayEndHour(),
                candidate.windStopMps(), candidate.enforceDailyInspectionGate(),
                candidate.maintenanceIntervalHours(), actor.id());
        return SiteSafetySettingsResponse.of(repo.save(s));
    }

    private void ensureCanManage(Long siteId, AuthenticatedUser actor) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "현장을 찾을 수 없습니다"));
        if (actor.role() == Role.ADMIN) return;
        if (actor.role() == Role.BP && site.getBpCompanyId().equals(actor.companyId())) return;
        throw ApiException.forbidden("SITE_MANAGE_DENIED", "현장 안전설정 권한이 없습니다");
    }

    private static double orDefault(Double v, double def) { return v != null ? v : def; }
    private static int orDefault(Integer v, int def) { return v != null ? v : def; }
}
