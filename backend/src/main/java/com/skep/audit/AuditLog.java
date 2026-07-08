package com.skep.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 감사 로그. 누가 어떤 데이터에 어떤 액션을 했는지 기록.
 * 알림(notifications) / 도메인 이력(equipment_site_assignments 등) 과는 분리한다.
 *
 * before_json/after_json 은 PostgreSQL JSONB 컬럼이지만, 단순화를 위해
 * 문자열로 저장한다. 필요 시 객체 → JSON 직렬화는 caller 가 한다.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(name = "actor_company_id")
    private Long actorCompanyId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "target_company_id")
    private Long targetCompanyId;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "before_json", columnDefinition = "text")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "text")
    private String afterJson;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AuditLog(Long actorUserId, String actorRole, Long actorCompanyId,
                     String action, String targetType, Long targetId,
                     Long targetCompanyId, Long siteId,
                     String beforeJson, String afterJson,
                     String ipAddress, String userAgent) {
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.actorCompanyId = actorCompanyId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetCompanyId = targetCompanyId;
        this.siteId = siteId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
