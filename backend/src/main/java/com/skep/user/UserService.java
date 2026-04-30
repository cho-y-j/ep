package com.skep.user;

import com.skep.common.ApiException;
import com.skep.security.RefreshTokenRepository;
import com.skep.user.dto.CreateUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("EMAIL_EXISTS", "email already in use");
        }
        User user = User.builder()
                .email(req.email())
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

    public User enable(Long id) {
        User u = get(id);
        u.enable();
        return u;
    }

    public User disable(Long id, Long actorId) {
        User u = get(id);
        if (u.getId().equals(actorId)) {
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "본인 계정은 비활성화할 수 없습니다");
        }
        if (u.getRole() == Role.ADMIN) {
            long activeAdmins = users.findAll().stream()
                    .filter(other -> other.getRole() == Role.ADMIN && other.isEnabled() && !other.getId().equals(u.getId()))
                    .count();
            if (activeAdmins == 0) {
                throw ApiException.badRequest("LAST_ADMIN", "마지막 활성 관리자는 비활성화할 수 없습니다");
            }
        }
        u.disable();
        refreshTokens.revokeAllByUserId(u.getId());
        return u;
    }
}
