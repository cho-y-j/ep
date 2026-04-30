package com.skep.auth;

import com.skep.auth.dto.LoginRequest;
import com.skep.auth.dto.SignupRequest;
import com.skep.auth.dto.TokenResponse;
import com.skep.common.ApiException;
import com.skep.config.JwtProperties;
import com.skep.security.JwtService;
import com.skep.security.RefreshToken;
import com.skep.security.RefreshTokenRepository;
import com.skep.security.RefreshTokenSecurityService;
import com.skep.user.Role;
import com.skep.user.User;
import com.skep.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenSecurityService refreshSecurity;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       RefreshTokenSecurityService refreshSecurity,
                       PasswordEncoder encoder,
                       JwtService jwt,
                       JwtProperties jwtProps) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.refreshSecurity = refreshSecurity;
        this.encoder = encoder;
        this.jwt = jwt;
        this.jwtProps = jwtProps;
    }

    public User signup(SignupRequest req) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.forbidden("ADMIN_SIGNUP_NOT_ALLOWED", "ADMIN role cannot self-signup");
        }
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("EMAIL_EXISTS", "email already in use");
        }
        User user = User.builder()
                .email(req.email())
                .password(encoder.encode(req.password()))
                .name(req.name())
                .phone(req.phone())
                .role(req.role())
                .isCompanyAdmin(false)
                .enabled(false)
                .build();
        return users.save(user);
    }

    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "email or password incorrect"));
        if (!encoder.matches(req.password(), user.getPassword())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "email or password incorrect");
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "account is awaiting approval");
        }
        return issueTokens(user);
    }

    public TokenResponse refresh(String rawRefresh) {
        String hash = jwt.hashRefreshToken(rawRefresh);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "refresh token not found"));

        if (!stored.isUsable()) {
            // suspected reuse — revoke all tokens for this user (separate tx so it persists past the throw)
            refreshSecurity.revokeAllForUser(stored.getUserId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "refresh token already used or expired");
        }

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user no longer exists"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "account is disabled");
        }

        // rotation: revoke old, issue new
        stored.revoke();
        return issueTokens(user);
    }

    public void logout(String rawRefresh) {
        String hash = jwt.hashRefreshToken(rawRefresh);
        refreshTokens.findByTokenHash(hash).ifPresent(RefreshToken::revoke);
    }

    private TokenResponse issueTokens(User user) {
        String access = jwt.issueAccessToken(user);
        String refresh = jwt.generateRefreshToken();
        LocalDateTime expiresAt = LocalDateTime.ofInstant(jwt.refreshExpiry(), ZoneId.systemDefault());

        RefreshToken token = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(jwt.hashRefreshToken(refresh))
                .expiresAt(expiresAt)
                .build();
        refreshTokens.save(token);

        return TokenResponse.of(access, refresh, jwtProps.accessTokenTtlMinutes() * 60L);
    }
}
