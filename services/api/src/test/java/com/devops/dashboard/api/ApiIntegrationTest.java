package com.devops.dashboard.api;

import com.devops.dashboard.shared.model.*;
import com.devops.dashboard.shared.repository.*;
import com.devops.dashboard.shared.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private ProjectRepository projectRepo;
    @MockitoBean private EnvironmentRepository envRepo;
    @MockitoBean private DeploymentRepository deployRepo;
    @MockitoBean private AlertRepository alertRepo;
    @MockitoBean private AuditLogRepository auditRepo;
    @MockitoBean private UserRepository userRepo;
    @MockitoBean private RoleRepository roleRepo;

    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        adminToken = jwtUtil.generateToken(UUID.randomUUID().toString(), "admin@test.com", List.of("admin"));
        viewerToken = jwtUtil.generateToken(UUID.randomUUID().toString(), "viewer@test.com", List.of("viewer"));
    }

    // ---- Health ----

    @Test @Order(1)
    void health_Public() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").exists());
    }

    // ---- Projects ----

    @Test @Order(10)
    void projects_ListRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(11)
    void projects_ListWithToken() throws Exception {
        Project p = Project.builder().id(UUID.randomUUID()).name("Test").slug("test").build();
        when(projectRepo.findByIsArchivedFalseOrderByCreatedAtDesc()).thenReturn(List.of(p));

        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test @Order(12)
    void projects_GetById() throws Exception {
        UUID id = UUID.randomUUID();
        Project p = Project.builder().id(id).name("Test").slug("test").build();
        when(projectRepo.findById(id)).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test"));
    }

    @Test @Order(13)
    void projects_GetByIdNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectRepo.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/" + id)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    // ---- Deployments (read-only) ----

    @Test @Order(20)
    void deployments_ListWithPagination() throws Exception {
        Page<Deployment> page = new PageImpl<>(List.of());
        when(deployRepo.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/deployments?page=0&size=10")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test @Order(21)
    void deployments_Stats() throws Exception {
        when(deployRepo.countPending()).thenReturn(3L);
        when(deployRepo.count()).thenReturn(100L);

        mockMvc.perform(get("/api/v1/deployments/stats")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(3))
                .andExpect(jsonPath("$.total").value(100));
    }

    // ---- Alerts ----

    @Test @Order(30)
    void alerts_List() throws Exception {
        when(alertRepo.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---- Audit Logs (admin only) ----

    @Test @Order(40)
    void auditLogs_AdminAccess() throws Exception {
        when(auditRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test @Order(41)
    void auditLogs_ViewerDenied() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test @Order(42)
    void auditLogs_NoToken() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isUnauthorized());
    }
}
