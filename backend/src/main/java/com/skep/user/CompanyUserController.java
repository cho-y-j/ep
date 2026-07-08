package com.skep.user;

import com.skep.auth.dto.UserResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import com.skep.user.dto.CreateCompanyUserRequest;
import com.skep.user.dto.UpdateCompanyUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 회사 master 가 같은 회사 하위 직원을 관리. */
@RestController
@RequestMapping("/api/companies/me/users")
public class CompanyUserController {

    private final CompanyUserService service;

    public CompanyUserController(CompanyUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor).stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateCompanyUserRequest req,
                               @CurrentUser AuthenticatedUser actor) {
        return UserResponse.from(service.create(req, actor));
    }

    @PostMapping("/{id}/approve")
    public UserResponse approve(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return UserResponse.from(service.approve(id, actor));
    }

    @PostMapping("/{id}/disable")
    public UserResponse disable(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return UserResponse.from(service.disable(id, actor));
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                                @Valid @RequestBody UpdateCompanyUserRequest req,
                                @CurrentUser AuthenticatedUser actor) {
        return UserResponse.from(service.update(id, req, actor));
    }
}
