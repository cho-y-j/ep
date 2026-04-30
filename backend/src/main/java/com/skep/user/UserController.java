package com.skep.user;

import com.skep.auth.dto.UserResponse;
import com.skep.user.dto.CreateUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserResponse> list() {
        return service.listAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return UserResponse.from(service.create(req));
    }

    @PatchMapping("/{id}/enable")
    public UserResponse enable(@PathVariable Long id) {
        return UserResponse.from(service.enable(id));
    }

    @PatchMapping("/{id}/disable")
    public UserResponse disable(@PathVariable Long id) {
        return UserResponse.from(service.disable(id));
    }
}
