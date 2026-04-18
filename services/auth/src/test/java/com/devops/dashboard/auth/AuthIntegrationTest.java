package com.devops.dashboard.auth;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Auth service REST API.
 *
 * <p>Uses MockBean to isolate the service from a real database — the
 * H2 application-test.yml exists primarily to satisfy Spring JPA
 * auto-configuration; actual repository calls are stubbed via Mockito.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepo;

    @MockBean
    private RoleRepository roleRepo;

    @MockBean
    private AuditLogRepository auditRepo;

    // ---- shared fixtures ---------------------------------------------------

    private User buildTestUser(String email, String username, String rawPassword, String... roleNames) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(username)
                .build();
        for (String rn : roleNames) {
            Role r = Role.builder().id(UUID.randomUUID()).name(rn).build();
            user.getRoles().add(r);
        }
        return user;
    }

    @BeforeEach
    void resetMocks() {
        reset(userRepo, roleRepo, auditRepo);
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ========================================================================
    //  Health endpoint
    // ========================================================================

    @Test
    @Order(1)
    void health_ReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("auth-test"));
    }

    // ========================================================================
    //  Registration
    // ========================================================================

    @Test
    @Order(10)
    void register_Success() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("alice@example.com")
                .username("alice")
                .password("strongPass99")
                .displayName("Alice Chen")
                .build();

        when(userRepo.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepo.existsByUsername("alice")).thenReturn(false);

        Role viewer = Role.builder().id(UUID.randomUUID()).name("viewer").build();
        when(roleRepo.findByName("viewer")).thenReturn(Optional.of(viewer));

        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value("alice@example.com"))
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.roles[0]").value("viewer"));

        verify(auditRepo, times(1)).save(any(AuditLog.class));
    }

    @Test
    @Order(11)
    void register_DuplicateEmail() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("taken@example.com")
                .username("newuser")
                .password("strongPass99")
                .build();

        when(userRepo.existsByEmail("taken@example.com")).thenReturn(true);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    @Order(12)
    void register_ValidationErrors() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .email("not-an-email")
                .username("ab")          // too short (min 3)
                .password("short")       // too short (min 8)
                .build();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.username").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    @Order(13)
    void register_EmptyBody() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.username").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    // ========================================================================
    //  Login
    // ========================================================================

    @Test
    @Order(20)
    void login_Success() throws Exception {
        User user = buildTestUser("bob@example.com", "bob", "securePass1", "developer");
        when(userRepo.findActiveByEmailWithRoles("bob@example.com"))
                .thenReturn(Optional.of(user));

        LoginRequest req = LoginRequest.builder()
                .email("bob@example.com")
                .password("securePass1")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value("bob@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("developer"));
    }

    @Test
    @Order(21)
    void login_WrongPassword() throws Exception {
        User user = buildTestUser("bob@example.com", "bob", "securePass1", "developer");
        when(userRepo.findActiveByEmailWithRoles("bob@example.com"))
                .thenReturn(Optional.of(user));

        LoginRequest req = LoginRequest.builder()
                .email("bob@example.com")
                .password("wrongPassword")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Order(22)
    void login_UserNotFound() throws Exception {
        when(userRepo.findActiveByEmailWithRoles("ghost@example.com"))
                .thenReturn(Optional.empty());

        LoginRequest req = LoginRequest.builder()
                .email("ghost@example.com")
                .password("anything")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ========================================================================
    //  /auth/me  (requires valid JWT)
    // ========================================================================

    @Test
    @Order(30)
    void me_WithValidToken() throws Exception {
        User user = buildTestUser("carol@example.com", "carol", "pass1234", "admin");
        String token = jwtUtil.generateToken(
                user.getId().toString(), user.getEmail(), List.of("admin"));

        when(userRepo.findByIdWithRoles(user.getId())).thenReturn(Optional.of(user));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("carol@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("admin"));
    }

    @Test
    @Order(31)
    void me_WithoutToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @Order(32)
    void me_WithInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer this.is.garbage"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    //  Protected route  (admin only)
    // ========================================================================

    @Test
    @Order(40)
    void adminRoute_WithAdminToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(
                userId.toString(), "admin@example.com", List.of("admin"));

        mockMvc.perform(get("/auth/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(41)
    void adminRoute_WithNonAdminToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(
                userId.toString(), "viewer@example.com", List.of("viewer"));

        mockMvc.perform(get("/auth/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(42)
    void adminRoute_WithoutToken() throws Exception {
        mockMvc.perform(get("/auth/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    //  Full flow — register then use the returned JWT
    // ========================================================================

    @Test
    @Order(50)
    void fullFlow_RegisterThenCallMe() throws Exception {
        // --- register ---
        RegisterRequest regReq = RegisterRequest.builder()
                .email("fullflow@example.com")
                .username("fullflow")
                .password("strongPass99")
                .build();

        when(userRepo.existsByEmail(any())).thenReturn(false);
        when(userRepo.existsByUsername(any())).thenReturn(false);
        Role viewer = Role.builder().id(UUID.randomUUID()).name("viewer").build();
        when(roleRepo.findByName("viewer")).thenReturn(Optional.of(viewer));

        UUID userId = UUID.randomUUID();
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            return u;
        });

        MvcResult regResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse regResp = objectMapper.readValue(
                regResult.getResponse().getContentAsString(), AuthResponse.class);

        // --- use the token to call /auth/me ---
        User savedUser = buildTestUser("fullflow@example.com", "fullflow", "x", "viewer");
        savedUser.setId(userId);
        when(userRepo.findByIdWithRoles(userId)).thenReturn(Optional.of(savedUser));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + regResp.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("fullflow@example.com"));
    }
}
