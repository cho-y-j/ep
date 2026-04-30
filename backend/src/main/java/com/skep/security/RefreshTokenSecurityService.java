package com.skep.security;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenSecurityService {

    private final RefreshTokenRepository repo;

    public RefreshTokenSecurityService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /**
     * Revokes all active refresh tokens for a user in a separate transaction so the
     * effect persists even when the caller throws afterwards (theft-detection path).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {
        repo.revokeAllByUserId(userId);
    }
}
