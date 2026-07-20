package com.skep.auth;

import com.skep.auth.dto.LoginRequest;
import com.skep.auth.dto.SignupRequest;
import com.skep.auth.dto.TokenResponse;
import com.skep.common.ApiException;
import com.skep.company.Company;
import com.skep.company.CompanyService;
import com.skep.company.CompanyType;
import com.skep.config.JwtProperties;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
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
    private final CompanyService companies;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;
    private final NotificationService notifications;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       RefreshTokenSecurityService refreshSecurity,
                       CompanyService companies,
                       PasswordEncoder encoder,
                       JwtService jwt,
                       JwtProperties jwtProps,
                       NotificationService notifications) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.refreshSecurity = refreshSecurity;
        this.companies = companies;
        this.encoder = encoder;
        this.jwt = jwt;
        this.jwtProps = jwtProps;
        this.notifications = notifications;
    }

    public User signup(SignupRequest req) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.forbidden("ADMIN_SIGNUP_NOT_ALLOWED", "ADMIN role cannot self-signup");
        }
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "email already in use");
        }

        Long companyId = null;
        Company company = null;
        if (req.role().requiresCompany()) {
            if (isBlank(req.companyName()) || isBlank(req.businessNumber())) {
                throw ApiException.badRequest("COMPANY_INFO_REQUIRED", "회사명과 사업자번호가 필요합니다");
            }
            CompanyType type = CompanyType.fromRole(req.role());
            CompanyService.CompanyResolution resolution = companies.resolveOrCreate(
                    req.companyName(), req.businessNumber(), type);
            company = resolution.company();
            companyId = company.getId();
        }

        // master(isCompanyAdmin) 권한은 자동 부여하지 않는다.
        // 첫 가입자도 ADMIN 승인 시 enabled + isCompanyAdmin 을 별도로 지정해야 함.
        // 사업자번호만 알면 임의 회사 master 가 되는 race 차단.
        User user = User.builder()
                .email(email)
                .password(encoder.encode(req.password()))
                .name(req.name())
                .phone(req.phone())
                .role(req.role())
                .companyId(companyId)
                .isCompanyAdmin(false)
                .enabled(false)
                .build();
        User saved = users.save(user);

        // V77: 자가가입한 회사가 상위(부모) 공급사를 가지면, 부모 회사에 승인 대기 인앱 알림 1건.
        // 부모 링크 없는 독립 회사는 알림 없음 → 기존 ADMIN 승인 게이트 그대로. (계정 생성 동작은 위와 동일 — 불변.)
        if (company != null && company.getParentCompanyId() != null) {
            notifications.sendToCompany(company.getParentCompanyId(),
                    NotificationType.SUB_SUPPLIER_SIGNUP,
                    "하위 공급사 가입 신청",
                    company.getName() + " — " + saved.getName() + " 님이 가입을 신청했습니다. 승인이 필요합니다.",
                    "SUB_SUPPLIER", company.getId(), null);
        }
        // 독립(부모 없음 또는 회사 없음) 가입은 위 분기에서 빠진다 → ADMIN 승인 게이트 대상.
        // ADMIN 시스템 알림 1건. 하위공급사 분기와 상호배타라 중복 발송 없음.
        if (company == null || company.getParentCompanyId() == null) {
            String companyLabel = company != null ? company.getName()
                    : (isBlank(req.companyName()) ? "-" : req.companyName().trim());
            notifications.sendSystem(NotificationType.USER_SIGNUP,
                    "새 가입 신청",
                    companyLabel + " " + saved.getName() + " " + saved.getRole().name(),
                    "USER", saved.getId(), null);
        }
        return saved;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(normalizeEmail(req.email()))
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

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED", "refresh token expired");
        }

        // atomic CAS: 동시 요청이 들어와도 revoke 성공한 1건만 새 토큰을 받는다. 이미 revoke 면 reuse.
        int updated = refreshTokens.revokeIfActive(stored.getId());
        if (updated == 0) {
            refreshSecurity.revokeAllForUser(stored.getUserId());
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "refresh token already used");
        }

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user no longer exists"));
        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "account is disabled");
        }

        return issueTokens(user);
    }

    public void logout(String rawRefresh) {
        String hash = jwt.hashRefreshToken(rawRefresh);
        refreshTokens.findByTokenHash(hash).ifPresent(RefreshToken::revoke);
    }

    /** Self-service 비밀번호 변경 — current-password 검증 + 모든 refresh 강제 무효화. */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
        if (!encoder.matches(currentPassword, user.getPassword())) {
            throw ApiException.unauthorized("INVALID_CURRENT_PASSWORD", "현재 비밀번호가 일치하지 않습니다");
        }
        if (encoder.matches(newPassword, user.getPassword())) {
            throw ApiException.badRequest("SAME_PASSWORD", "현재 비밀번호와 같습니다");
        }
        user.changePassword(encoder.encode(newPassword));
        refreshTokens.revokeAllByUserId(user.getId());
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
