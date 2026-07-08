package com.skep.security;

import com.skep.config.JwtProperties;
import com.skep.user.Role;
import com.skep.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;

@Component
public class JwtService {

    private final JwtProperties props;
    private final SecretKey signingKey;
    private final SecureRandom random = new SecureRandom();

    // dev/test placeholder. prod profile 활성화 시 이 값이 사용되면 부팅 거부.
    private static final Set<String> KNOWN_DEV_SECRETS = Set.of(
            "dev-only-secret-change-in-prod-must-be-at-least-32-bytes-long",
            "change_me_jwt_must_be_at_least_32_bytes_long"
    );

    public JwtService(JwtProperties props, Environment env) {
        this.props = props;
        String secret = props.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes — set JWT_SECRET env var");
        }
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (isProd && KNOWN_DEV_SECRETS.contains(secret)) {
            throw new IllegalStateException(
                    "JWT secret is a development placeholder — set a real JWT_SECRET (32+ bytes) in prod profile");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofMinutes(props.accessTokenTtlMinutes()));
        var builder = Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));
        if (user.getCompanyId() != null) {
            builder.claim("company_id", user.getCompanyId());
        }
        // 회사 관리자/일반 직원 분리 — 감사 로그 / 회사 범위 조회 권한 분기에 사용된다.
        builder.claim("is_company_admin", user.isCompanyAdmin());
        return builder.signWith(signingKey).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public Role extractRole(String token) {
        return Role.valueOf(parse(token).get("role", String.class));
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String hashRefreshToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Instant refreshExpiry() {
        return Instant.now().plus(Duration.ofDays(props.refreshTokenTtlDays()));
    }
}
