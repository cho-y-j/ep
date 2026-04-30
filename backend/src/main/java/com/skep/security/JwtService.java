package com.skep.security;

import com.skep.config.JwtProperties;
import com.skep.user.Role;
import com.skep.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

@Component
public class JwtService {

    private final JwtProperties props;
    private final SecretKey signingKey;
    private final SecureRandom random = new SecureRandom();

    public JwtService(JwtProperties props) {
        this.props = props;
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
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
