package com.devops.dashboard.deployment;

import com.devops.dashboard.deployment.dto.CreateDeploymentRequest;
import com.devops.dashboard.deployment.dto.UpdateStatusRequest;
import com.devops.dashboard.shared.model.*;
import com.devops.dashboard.shared.repository.*;
import com.devops.dashboard.shared.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
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
class DeploymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtUtil jwtUtil;

    @MockBean private DeploymentRepository deployRepo;
    @MockBean private EnvironmentRepository envRepo;
    @MockBean private ProjectRepository projectRepo;
    @MockBean private UserRepository userRepo;
    @MockBean private AuditLogRepository auditRepo;
    @MockBean private AlertRepository alertRepo;
    @MockBean private RoleRepository roleRepo;

    private String deployerToken;
    private UUID deployerUserId;

    @BeforeEach
    void setUp() {
        reset(deployRepo, envRepo, projectRepo, userRepo, auditRepo);
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        deployerUserId = UUID.randomUUID();
        deployerToken = jwtUtil.generateToken(
                deployerUserId.toString(), "dev@test.com", List.of("developer"));
    }

    // ---- Health ----

    @Test @Order(1)
    void health_Public() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").exists());
    }

    // ---- List ----

    @Test @Order(10)
    void list_RequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/deployments"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(11)
    void list_Paginated() throws Exception {
        when(deployRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/deployments?page=0&size=10")
                        .header("Authorization", "Bearer " + deployerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.page").value(0));
    }

    // ---- Create ----

    @Test @Order(20)
    void create_Success() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();

        Project project = Project.builder().id(projectId).name("P").slug("p").build();
        Environment env = Environment.builder().id(envId).name("staging").project(project).isLocked(false).build();
        User user = User.builder().id(deployerUserId).email("dev@test.com").username("dev").build();

        when(envRepo.findById(envId)).thenReturn(Optional.of(env));
        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(userRepo.findById(deployerUserId)).thenReturn(Optional.of(user));
        when(deployRepo.save(any(Deployment.class))).thenAnswer(inv -> {
            Deployment d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        CreateDeploymentRequest req = CreateDeploymentRequest.builder()
                .projectId(projectId)
                .environmentId(envId)
                .gitRef("main")
                .gitSha("abc123")
                .build();

        mockMvc.perform(post("/api/v1/deployments")
                        .header("Authorization", "Bearer " + deployerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").exists());

        verify(auditRepo, times(1)).save(any(AuditLog.class));
    }

    @Test @Order(21)
    void create_LockedEnvironment() throws Exception {
        UUID envId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Environment env = Environment.builder().id(envId).name("prod").isLocked(true).build();
        when(envRepo.findById(envId)).thenReturn(Optional.of(env));

        CreateDeploymentRequest req = CreateDeploymentRequest.builder()
                .projectId(projectId)
                .environmentId(envId)
                .build();

        mockMvc.perform(post("/api/v1/deployments")
                        .header("Authorization", "Bearer " + deployerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test @Order(22)
    void create_ViewerDenied() throws Exception {
        String viewerToken = jwtUtil.generateToken(
                UUID.randomUUID().toString(), "viewer@test.com", List.of("viewer"));

        CreateDeploymentRequest req = CreateDeploymentRequest.builder()
                .projectId(UUID.randomUUID())
                .environmentId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/v1/deployments")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ---- Update status ----

    @Test @Order(30)
    void updateStatus_Success() throws Exception {
        UUID deploymentId = UUID.randomUUID();
        Deployment existing = Deployment.builder()
                .id(deploymentId)
                .status(Deployment.DeploymentStatus.pending)
                .build();

        when(deployRepo.findById(deploymentId)).thenReturn(Optional.of(existing));
        when(deployRepo.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateStatusRequest req = UpdateStatusRequest.builder()
                .status("in_progress")
                .build();

        mockMvc.perform(put("/api/v1/deployments/" + deploymentId + "/status")
                        .header("Authorization", "Bearer " + deployerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_progress"));
    }

    // ---- Stats ----

    @Test @Order(40)
    void stats() throws Exception {
        when(deployRepo.countPending()).thenReturn(5L);
        when(deployRepo.count()).thenReturn(42L);

        mockMvc.perform(get("/api/v1/deployments/stats")
                        .header("Authorization", "Bearer " + deployerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(5))
                .andExpect(jsonPath("$.total").value(42));
    }
}
