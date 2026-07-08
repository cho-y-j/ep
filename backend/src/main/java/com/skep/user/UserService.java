package com.skep.user;

import com.skep.common.ApiException;
import com.skep.security.RefreshTokenRepository;
import com.skep.user.dto.CreateUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return users.findAll();
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "user " + id + " not found"));
    }

    public User create(CreateUserRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_EXISTS", "email already in use");
        }
        User user = User.builder()
                .email(email)
                .password(encoder.encode(req.password()))
                .name(req.name())
                .phone(req.phone())
                .role(req.role())
                .companyId(req.companyId())
                .isCompanyAdmin(Boolean.TRUE.equals(req.isCompanyAdmin()))
                .enabled(true)
                .build();
        return users.save(user);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * ADMIN 이 가입 신청을 승인할 때 호출. enabled=true 로 전이.
     * 회사에 master 가 한 명도 없으면 이 사용자를 자동으로 master 로 승격 (운영 흐름 유지).
     * 가입 시점에 자동 부여하지 않는 이유 — 임의의 사업자번호로 부적격자가 master 가 되는 race 차단.
     */
    public User enable(Long id) {
        User u = get(id);
        u.enable();
        if (u.getCompanyId() != null && !u.isCompanyAdmin()) {
            long currentMasters = users.countByCompanyIdAndIsCompanyAdminTrue(u.getCompanyId());
            if (currentMasters == 0) {
                u.setIsCompanyAdmin(true);
            }
        }
        return u;
    }

    public User disable(Long id, Long actorId) {
        User u = get(id);
        if (u.getId().equals(actorId)) {
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "본인 계정은 비활성화할 수 없습니다");
        }
        if (u.getRole() == Role.ADMIN) {
            long activeAdmins = users.countByRoleAndEnabledAndIdNot(Role.ADMIN, true, u.getId());
            if (activeAdmins == 0) {
                throw ApiException.badRequest("LAST_ADMIN", "마지막 활성 관리자는 비활성화할 수 없습니다");
            }
        }
        u.disable();
        refreshTokens.revokeAllByUserId(u.getId());
        return u;
    }
}
