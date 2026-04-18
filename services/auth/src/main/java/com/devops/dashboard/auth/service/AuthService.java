package com.devops.dashboard.auth.service;

import com.devops.dashboard.auth.dto.AuthResponse;
import com.devops.dashboard.auth.dto.LoginRequest;
import com.devops.dashboard.auth.dto.RegisterRequest;
import com.devops.dashboard.shared.security.JwtUtil;
import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.model.Role;
import com.devops.dashboard.shared.model.User;
import com.devops.dashboard.shared.repository.AuditLogRepository;
import com.devops.dashboard.shared.repository.RoleRepository;
import com.devops.dashboard.shared.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Core authentication and registration business logic.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DEFAULT_ROLE = "viewer";

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final AuditLogRepository auditRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo,
                       RoleRepository roleRepo,
                       AuditLogRepository auditRepo,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.auditRepo = auditRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User user = User.builder()
                .email(req.getEmail())
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .displayName(req.getDisplayName())
                .build();

        roleRepo.findByName(DEFAULT_ROLE)
                .ifPresent(role -> user.getRoles().add(role));

        User saved = userRepo.save(user);

        auditRepo.save(AuditLog.builder()
                .actorId(saved.getId())
                .action("user.created")
                .entityType("user")
                .entityId(saved.getId())
                .diff(Map.of("after", Map.of("email", saved.getEmail(), "username", saved.getUsername())))
                .build());

        log.info("User registered: id={}, email={}", saved.getId(), saved.getEmail());
        return buildAuthResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findActiveByEmailWithRoles(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email={}", req.getEmail());
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepo.save(user);

        auditRepo.save(AuditLog.builder()
                .actorId(user.getId())
                .action("user.login")
                .entityType("user")
                .entityId(user.getId())
                .diff(Map.of())
                .build());

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        String token = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), roleNames);
        return AuthResponse.builder()
                .token(token)
                .user(AuthResponse.UserProfile.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .status(user.getStatus().name())
                        .roles(roleNames)
                        .createdAt(user.getCreatedAt())
                        .build())
                .build();
    }
}
