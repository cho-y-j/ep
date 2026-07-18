package com.skep.notification;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 일괄 read 처리용 — 가시 + 미읽음 만 가져온다. */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.readAt IS NULL
              AND (
                :includeSystem = true
                OR n.targetUserId = :userId
                OR (n.targetUserId IS NULL AND n.targetCompanyId = :companyId)
              )
            """)
    List<Notification> findUnreadFor(@Param("userId") Long userId,
                                     @Param("companyId") Long companyId,
                                     @Param("includeSystem") boolean includeSystem);

    /** ADMIN 전체. */
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * P4d: 가시성 + 필터 통합 목록.
     * - includeAll=true (ADMIN): 전체. 그 외: 직접(userId) + 회사 broadcast(companyId).
     * - has* 플래그로 각 필터 on/off. (PostgreSQL 은 IS NULL 만 있는 null 바인드의 타입을 추론 못 하므로
     *   모든 바인드 파라미터를 non-null 로 넘기고 boolean 가드로 제어.)
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE (
                    :includeAll = true
                 OR n.targetUserId = :userId
                 OR (n.targetUserId IS NULL AND n.targetCompanyId = :companyId)
                  )
              AND (:hasUnread = false
                   OR (:unreadVal = true AND n.readAt IS NULL)
                   OR (:unreadVal = false AND n.readAt IS NOT NULL))
              AND (:hasTypes = false OR n.type IN :typeList)
              AND (:hasFrom = false OR n.createdAt >= :fromTs)
              AND (:hasQ = false
                   OR LOWER(n.title) LIKE :qLike
                   OR LOWER(n.message) LIKE :qLike
                   OR (n.senderLabel IS NOT NULL AND LOWER(n.senderLabel) LIKE :qLike))
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findFiltered(@Param("includeAll") boolean includeAll,
                                    @Param("userId") Long userId,
                                    @Param("companyId") Long companyId,
                                    @Param("hasUnread") boolean hasUnread,
                                    @Param("unreadVal") boolean unreadVal,
                                    @Param("hasTypes") boolean hasTypes,
                                    @Param("typeList") List<String> typeList,
                                    @Param("hasFrom") boolean hasFrom,
                                    @Param("fromTs") java.time.LocalDateTime fromTs,
                                    @Param("hasQ") boolean hasQ,
                                    @Param("qLike") String qLike,
                                    Pageable pageable);

    /** ADMIN 전체 미읽음 수. */
    long countByReadAtIsNull();

    /** 만료 임박 스케줄러 중복 생성 가드 — 같은 날 같은 (회사, type, 링크대상) 알림이 이미 있는지. */
    boolean existsByTargetCompanyIdAndTypeAndLinkTypeAndLinkIdAndCreatedAtGreaterThanEqual(
            Long targetCompanyId, String type, String linkType, Long linkId,
            java.time.LocalDateTime createdAtFrom);

    /** 월마감 스케줄러 중복 생성 가드 — 이번 달 같은 (회사, type) 알림이 이미 있는지. */
    boolean existsByTargetCompanyIdAndTypeAndCreatedAtGreaterThanEqual(
            Long targetCompanyId, String type, java.time.LocalDateTime createdAtFrom);

    /**
     * 사용자 가시성 알림 조회.
     *
     * - target_user_id == :userId 인 직접 알림
     * - target_user_id IS NULL 이고 target_company_id == :companyId 인 회사 broadcast
     * - target_user_id IS NULL 이고 target_company_id IS NULL 인 시스템 broadcast
     *   (ADMIN 인 경우만; 호출자 측에서 system 포함 여부 결정 — 여기선 단순히 둘 다 필터에 포함)
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.targetUserId = :userId
               OR (n.targetUserId IS NULL AND n.targetCompanyId = :companyId)
               OR (:includeSystem = true AND n.targetUserId IS NULL AND n.targetCompanyId IS NULL)
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findVisibleFor(@Param("userId") Long userId,
                                      @Param("companyId") Long companyId,
                                      @Param("includeSystem") boolean includeSystem,
                                      Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.readAt IS NULL
              AND (
                n.targetUserId = :userId
                OR (n.targetUserId IS NULL AND n.targetCompanyId = :companyId)
                OR (:includeSystem = true AND n.targetUserId IS NULL AND n.targetCompanyId IS NULL)
              )
            """)
    long countUnread(@Param("userId") Long userId,
                     @Param("companyId") Long companyId,
                     @Param("includeSystem") boolean includeSystem);
}
