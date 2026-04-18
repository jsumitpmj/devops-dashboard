package com.devops.dashboard.auth.controller;

import com.devops.dashboard.auth.dto.AuthResponse;
import com.devops.dashboard.auth.dto.LoginRequest;
import com.devops.dashboard.auth.dto.RegisterRequest;
import com.devops.dashboard.auth.service.AuthService;
import com.devops.dashboard.shared.dto.ApiError;
import com.devops.dashboard.shared.dto.HealthResponse;
import com.devops.dashboard.shared.health.HealthChecker;
import com.devops.dashboard.shared.model.Role;
import com.devops.dashboard.shared.model.User;
import com.devops.dashboard.shared.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for authentication, registration, and profile.
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepo;
    private final HealthChecker healthChecker;

    public AuthController(AuthService authService,
                          UserRepository userRepo,
                          HealthChecker healthChecker) {
        this.authService = authService;
        this.userRepo = userRepo;
        this.healthChecker = healthChecker;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthChecker.check();
        int status = "ok".equals(response.getStatus()) ? 200 : 503;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (String) auth.getPrincipal();

        var userOpt = userRepo.findByIdWithRoles(UUID.fromString(userId));
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiError.builder().error("Not Found").message("User not found").statusCode(404).build());
        }

        User user = userOpt.get();
        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        return ResponseEntity.ok(Map.of(
                "user", AuthResponse.UserProfile.builder()
                        .id(user.getId()).email(user.getEmail()).username(user.getUsername())
                        .displayName(user.getDisplayName()).status(user.getStatus().name())
                        .roles(roles).createdAt(user.getCreatedAt()).build()));
    }

    @GetMapping("/auth/admin/dashboard")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<?> adminDashboard() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the admin dashboard",
                "info", "Only users with the 'admin' role can access this endpoint."));
    }
}
