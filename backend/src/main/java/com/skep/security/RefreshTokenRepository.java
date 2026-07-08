package com.skep.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllByUserId(@Param("userId") Long userId);

    /** Refresh rotation atomic CAS — 동시에 같은 token 으로 refresh 시도해도 1건만 성공. */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.id = :id AND r.revoked = false")
    int revokeIfActive(@Param("id") Long id);
}
