package com.skep.audit;

import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.site.Site;
import com.skep.site.SiteParticipantRepository;
import com.skep.site.SiteParticipantStatus;
import com.skep.site.SiteRepository;
import com.skep.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repo;
    private final SiteRepository sites;
    private final SiteParticipantRepository participants;

    public AuditLogService(AuditLogRepository repo, SiteRepository sites, SiteParticipantRepository participants) {
        this.repo = repo;
        this.sites = sites;
        this.participants = participants;
    }

    /**
     * 표준 기록 진입점. 도메인 서비스에서 비즈니스 작업 직후 호출한다.
     * 트랜잭션 실패 시 함께 롤백되어, 실제 변경되지 않은 액션은 로그도 남지 않는다.
     */
    public void record(AuthenticatedUser actor, String action, String targetType,
                       Long targetId, Long targetCompanyId, Long siteId,
                       String beforeJson, String afterJson) {
        try {
            AuditLog logEntry = AuditLog.builder()
                    .actorUserId(actor != null ? actor.id() : null)
                    .actorRole(actor != null && actor.role() != null ? actor.role().name() : null)
                    .actorCompanyId(actor != null ? actor.companyId() : null)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetCompanyId(targetCompanyId)
                    .siteId(siteId)
                    .beforeJson(beforeJson)
                    .afterJson(afterJson)
                    .build();
            repo.save(logEntry);
        } catch (Exception e) {
            // 로그 실패는 본 트랜잭션 원자성을 위해 throw. 다만 호출자 측에서 잡고 무시할지는 정책.
            log.error("audit log save failed action={} targetType={} targetId={}", action, targetType, targetId, e);
            throw e;
        }
    }

    /** 간단 호출용 오버로드 (회사/사이트 ID 없는 경우). */
    public void record(AuthenticatedUser actor, String action, String targetType, Long targetId, String afterJson) {
        record(actor, action, targetType, targetId, null, null, null, afterJson);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResult> list(AuthenticatedUser actor, int page, int size) {
        if (size < 1 || size > 100) size = 20;
        if (page < 0) page = 0;
        Pageable pageable = PageRequest.of(page, size);

        if (actor.role() == Role.ADMIN) {
            return repo.findAllByOrderByCreatedAtDesc(pageable).map(this::toResult);
        }
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 지정되지 않았습니다");
        }
        // 회사 범위 로그는 회사 관리자만 본다. 일반 직원은 자기 행동 로그(actor_user_id) 만.
        if (!actor.isCompanyAdmin()) {
            return repo.findByActorUserIdOrderByCreatedAtDesc(actor.id(), pageable).map(this::toResult);
        }

        Collection<Long> siteIds = scopedSiteIds(actor);
        // findForCompanyScope 의 site_id IN (...) 절은 빈 리스트면 H2/PG 모두 에러 → sentinel 추가.
        if (siteIds.isEmpty()) siteIds = List.of(-1L);
        return repo.findForCompanyScope(actor.companyId(), siteIds, pageable).map(this::toResult);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResult> recent(AuthenticatedUser actor, int limit) {
        if (limit < 1 || limit > 50) limit = 10;
        return list(actor, 0, limit).getContent();
    }

    /** 사용자가 볼 수 있는 site_id 모음 (BP 자기 현장 + 공급사 참여 현장). */
    private Collection<Long> scopedSiteIds(AuthenticatedUser actor) {
        if (actor.role() == Role.BP && actor.companyId() != null) {
            return sites.findByBpCompanyIdOrderByIdDesc(actor.companyId()).stream()
                    .map(Site::getId)
                    .toList();
        }
        if ((actor.role() == Role.EQUIPMENT_SUPPLIER || actor.role() == Role.MANPOWER_SUPPLIER)
                && actor.companyId() != null) {
            return participants
                    .findByCompanyIdAndStatusOrderByIdDesc(actor.companyId(), SiteParticipantStatus.ACTIVE)
                    .stream()
                    .map(p -> p.getSiteId())
                    .toList();
        }
        return Collections.emptyList();
    }

    private AuditLogResult toResult(AuditLog log) {
        return new AuditLogResult(log);
    }

    /** Repository → Controller 사이의 간단 wrapper (DTO 변환 의존성 분리용). */
    public record AuditLogResult(AuditLog log) {}
}
