package com.skep.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByEnabled(boolean enabled);
    List<User> findByEnabledOrderByCreatedAtDesc(boolean enabled);
    List<User> findByCompanyIdOrderByIdAsc(Long companyId);

    long countByCompanyIdAndIsCompanyAdminTrue(Long companyId);
    List<User> findByCompanyIdAndIsCompanyAdminTrue(Long companyId);

    long countByRoleAndEnabledAndIdNot(com.skep.user.Role role, boolean enabled, Long id);

    /** 현장 문제알림 FCM 푸시 대상 — BP 회사 사용자 중 토큰 등록된 사람. */
    List<User> findByCompanyIdAndFcmTokenIsNotNull(Long companyId);
}
