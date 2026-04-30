package com.skep.auth;

import com.skep.auth.dto.LoginRequest;
import com.skep.auth.dto.RefreshRequest;
import com.skep.auth.dto.SignupRequest;
import com.skep.auth.dto.TokenResponse;
import com.skep.auth.dto.UserResponse;
import com.skep.common.ApiException;
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

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
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

    @GetMapping("/me")
    public UserResponse me(@CurrentUser AuthenticatedUser principal) {
        if (principal == null) {
            throw ApiException.unauthorized("NOT_AUTHENTICATED", "no auth principal");
        }
        User u = users.findById(principal.id())
                .orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "user not found"));
        return UserResponse.from(u);
    }
}
