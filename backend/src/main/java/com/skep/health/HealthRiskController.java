package com.skep.health;

import com.skep.common.ApiException;
import com.skep.person.HealthRiskLevel;
import com.skep.person.Person;
import com.skep.person.PersonRepository;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.site.Site;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

/**
 * P5-W4 2겹 — 고위험군 태깅(건강검진 서류 기반 수동). ADMIN·BP. BP 는 자기 현장 배치 인원만.
 * HIGH 설정 시 워치 정책 YELLOW 상향(WatchPolicyService)·혈압 체크인 필수 대상에 자동 반영.
 */
@RestController
@RequestMapping("/api/persons/{personId}/health-risk")
@RequiredArgsConstructor
public class HealthRiskController {

    private final PersonRepository persons;
    private final SiteRepository sites;

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','BP')")
    @Transactional
    public Map<String, Object> setLevel(@PathVariable Long personId,
                                        @RequestBody LevelRequest req,
                                        @CurrentUser AuthenticatedUser actor) {
        if (req.level == null || req.level.isBlank()) {
            throw ApiException.badRequest("NO_LEVEL", "위험등급(level)을 지정하세요");
        }
        HealthRiskLevel level;
        try {
            level = HealthRiskLevel.valueOf(req.level.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_LEVEL", "위험등급은 NORMAL·CAUTION·HIGH 중 하나여야 합니다");
        }
        Person p = persons.findById(personId).orElseThrow(() ->
                ApiException.notFound("PERSON_NOT_FOUND", "작업자를 찾을 수 없습니다"));

        if (actor.role() == Role.BP) {
            Long siteId = p.getCurrentSiteId();
            boolean ownsSite = siteId != null && sites.findById(siteId)
                    .map(Site::getBpCompanyId).filter(bp -> Objects.equals(bp, actor.companyId())).isPresent();
            if (!ownsSite) throw ApiException.forbidden("DENIED", "본인 현장 배치 인원만 태깅할 수 있습니다");
        }

        p.setHealthRiskLevel(level);
        persons.save(p);
        return Map.of("person_id", p.getId(), "health_risk_level", p.getHealthRiskLevel().name());
    }

    public static class LevelRequest {
        public String level;   // NORMAL | CAUTION | HIGH
    }
}
