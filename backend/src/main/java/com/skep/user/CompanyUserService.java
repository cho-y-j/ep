package com.skep.user;

import com.skep.common.ApiException;
import com.skep.security.AuthenticatedUser;
import com.skep.security.RefreshTokenRepository;
import com.skep.user.dto.CreateCompanyUserRequest;
import com.skep.user.dto.UpdateCompanyUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회사 master(= isCompanyAdmin) 가 자기 회사 하위 직원을 관리하는 서비스.
 * - 등록: role/companyId/isCompanyAdmin 모두 서버에서 강제 (master 의 권한 상승 차단)
 * - 승인/비활성: target 의 companyId == actor.companyId 검증
 */
@Service
@Transactional
public class CompanyUserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;

    public CompanyUserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public List<User> list(AuthenticatedUser actor) {
        ensureMaster(actor);
        return users.findByCompanyIdOrderByIdAsc(actor.companyId());
    }

    public User create(CreateCompanyUserRequest req, AuthenticatedUser actor) {
        ensureMaster(actor);
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("EMAIL_EXISTS", "이미 사용 중인 이메일입니다");
        }
        User u = User.builder()
                .email(req.email())
                .password(encoder.encode(req.password()))
                .name(req.name())
                .phone(req.phone())
                .role(actor.role())
                .companyId(actor.companyId())
                .isCompanyAdmin(false)
                .enabled(true)
                .build();
        return users.save(u);
    }

    public User approve(Long targetId, AuthenticatedUser actor) {
        ensureMaster(actor);
        User u = getInSameCompany(targetId, actor);
        u.enable();
        return u;
    }

    public User update(Long targetId, UpdateCompanyUserRequest req, AuthenticatedUser actor) {
        ensureMaster(actor);
        User u = getInSameCompany(targetId, actor);
        // 다른 master 의 프로필은 본인만 수정.
        if (u.isCompanyAdmin() && !u.getId().equals(actor.id())) {
            throw ApiException.forbidden("CANNOT_EDIT_OTHER_MASTER", "다른 관리자 계정은 수정할 수 없습니다");
        }
        u.updateProfile(req.name(), req.phone());
        if (req.showInQuote() != null) {
            u.updateQuoteVisibility(req.showInQuote(), req.quoteDisplayOrder());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            // master 본인 비번은 self-service(/api/auth/change-password)로만 — current-password 검증 필요.
            if (u.getId().equals(actor.id())) {
                throw ApiException.badRequest("USE_SELF_SERVICE_PASSWORD",
                        "본인 비밀번호는 비밀번호 변경 메뉴를 이용하세요");
            }
            u.changePassword(encoder.encode(req.newPassword()));
            refreshTokens.revokeAllByUserId(u.getId());
        }
        return u;
    }

    public User disable(Long targetId, AuthenticatedUser actor) {
        ensureMaster(actor);
        if (actor.id().equals(targetId)) {
            throw ApiException.badRequest("CANNOT_DISABLE_SELF", "본인 계정은 비활성화할 수 없습니다");
        }
        User u = getInSameCompany(targetId, actor);
        if (u.isCompanyAdmin()) {
            throw ApiException.badRequest("CANNOT_DISABLE_MASTER", "다른 회사 관리자 계정은 비활성화할 수 없습니다");
        }
        u.disable();
        refreshTokens.revokeAllByUserId(u.getId());
        return u;
    }

    private User getInSameCompany(Long targetId, AuthenticatedUser actor) {
        User u = users.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다"));
        if (u.getCompanyId() == null || !u.getCompanyId().equals(actor.companyId())) {
            throw ApiException.forbidden("CROSS_COMPANY", "다른 회사 사용자입니다");
        }
        return u;
    }

    private void ensureMaster(AuthenticatedUser actor) {
        if (actor.companyId() == null) {
            throw ApiException.forbidden("NO_COMPANY", "소속 회사가 없습니다");
        }
        if (!actor.isCompanyAdmin()) {
            throw ApiException.forbidden("NOT_COMPANY_ADMIN", "회사 관리자만 사용 가능합니다");
        }
    }
}
