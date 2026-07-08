package com.skep.auth;

import com.skep.auth.dto.ChangePasswordRequest;
import com.skep.auth.dto.LoginRequest;
import com.skep.auth.dto.MeResponse;
import com.skep.auth.dto.RefreshRequest;
import com.skep.auth.dto.SignupRequest;
import com.skep.auth.dto.TokenResponse;
import com.skep.auth.dto.UserResponse;
import com.skep.common.ApiException;
import com.skep.company.CompanyRepository;
import com.skep.company.dto.CompanyResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.User;
import com.skep.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;
    private final CompanyRepository companies;

    public AuthController(AuthService auth, UserRepository users, CompanyRepository companies) {
        this.auth = auth;
        this.users = users;
        this.companies = companies;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@Valid @RequestBody SignupRequest req) {
        User u = auth.signup(req);
        return Map.of(
                "user", UserResponse.from(u),
                "message", "signup successful — awaiting admin approval"
        );
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                                @CurrentUser AuthenticatedUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("NOT_AUTHENTICATED", "no auth principal");
        }
        auth.changePassword(principal.id(), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** BP/ADMIN 모바일 앱 FCM 토큰 등록 — 작업자 현장 문제알림 푸시 수신용. (작업자용 field-auth/register-token 과 별개) */
    @PostMapping("/register-fcm-token")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> registerFcmToken(@RequestBody Map<String, String> body,
                                                @CurrentUser AuthenticatedUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("NOT_AUTHENTICATED", "no auth principal");
        }
        String token = body.get("fcm_token");
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("NO_FCM_TOKEN", "fcm_token 필수");
        }
        User u = users.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
        u.updateFcmToken(token.trim());
        users.save(u);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public MeResponse me(@CurrentUser AuthenticatedUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("NOT_AUTHENTICATED", "no auth principal");
        }
        User u = users.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
        CompanyResponse company = u.getCompanyId() == null ? null
                : companies.findById(u.getCompanyId()).map(CompanyResponse::from).orElse(null);
        return new MeResponse(UserResponse.from(u), company);
    }
}
