package com.devops.dashboard.worker;

import com.devops.dashboard.shared.model.*;
import com.devops.dashboard.shared.repository.*;
import com.devops.dashboard.worker.client.NotificationClient;
import com.devops.dashboard.worker.job.AlertEscalationJob;
import com.devops.dashboard.worker.job.DeploymentPollingJob;
import com.devops.dashboard.worker.job.EnvironmentSyncJob;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DeploymentPollingJob deployPollingJob;
    @Autowired private EnvironmentSyncJob envSyncJob;
    @Autowired private AlertEscalationJob alertEscalationJob;

    @MockBean private DeploymentRepository deployRepo;
    @MockBean private AlertRepository alertRepo;
    @MockBean private AuditLogRepository auditRepo;
    @MockBean private EnvironmentRepository envRepo;
    @MockBean private ProjectRepository projectRepo;
    @MockBean private UserRepository userRepo;
    @MockBean private RoleRepository roleRepo;
    @MockBean private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        reset(deployRepo, alertRepo, auditRepo, envRepo, notificationClient);
        when(deployRepo.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(alertRepo.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- Health ----

    @Test @Order(1)
    void health_ReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("worker"));
    }

    // ---- Worker stats ----

    @Test @Order(2)
    void stats_ReturnsQueueDepth() throws Exception {
        when(deployRepo.countPending()).thenReturn(7L);
        when(deployRepo.count()).thenReturn(123L);
        when(alertRepo.count()).thenReturn(45L);

        mockMvc.perform(get("/worker/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingDeployments").value(7))
                .andExpect(jsonPath("$.totalDeployments").value(123))
                .andExpect(jsonPath("$.totalAlerts").value(45));
    }

    // ---- Deployment polling job ----

    @Test @Order(10)
    void poll_NoStaleDeployments() {
        when(deployRepo.findStaleDeployments(any(OffsetDateTime.class), any()))
                .thenReturn(List.of());
        when(deployRepo.countPending()).thenReturn(0L);

        deployPollingJob.pollDeployments();

        verify(deployRepo, never()).save(any());
        verify(alertRepo, never()).save(any());
    }

    @Test @Order(11)
    void poll_StaleDeploymentMarkedFailed() {
        UUID depId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).name("P").slug("p").build();
        Environment env = Environment.builder().id(UUID.randomUUID()).name("staging").build();

        Deployment stale = Deployment.builder()
                .id(depId)
                .status(Deployment.DeploymentStatus.in_progress)
                .startedAt(OffsetDateTime.now().minusMinutes(45))
                .project(project)
                .environment(env)
                .build();

        when(deployRepo.findStaleDeployments(any(OffsetDateTime.class), any()))
                .thenReturn(List.of(stale));
        when(deployRepo.countPending()).thenReturn(0L);

        deployPollingJob.pollDeployments();

        // Verify status was changed
        verify(deployRepo, times(1)).save(argThat(d ->
                d.getStatus() == Deployment.DeploymentStatus.failed
                && d.getFinishedAt() != null
                && d.getErrorMessage().contains("Timed out")));

        // Verify alert was created
        verify(alertRepo, times(1)).save(argThat(a ->
                a.getTitle().contains("timed out")
                && a.getSeverity() == Alert.AlertSeverity.error));

        // Verify audit log
        verify(auditRepo, times(1)).save(argThat(al ->
                "deployment.timed_out".equals(al.getAction())));

        // Verify notification was sent
        verify(notificationClient, times(1)).sendNotification(
                eq("deployment.timed_out"), anyString(), anyString(),
                eq("error"), eq(project.getId()), eq(depId));
    }

    // ---- Environment sync job ----

    @Test @Order(20)
    void sync_LogsStats() {
        when(envRepo.count()).thenReturn(5L);
        when(deployRepo.count()).thenReturn(50L);
        when(deployRepo.countPending()).thenReturn(2L);

        // Should not throw
        envSyncJob.syncEnvironments();

        // No writes on a normal sync
        verify(auditRepo, never()).save(any());
    }

    // ---- Alert escalation job ----

    @Test @Order(30)
    void escalation_NoAlertsToEscalate() {
        when(alertRepo.findByStatusOrderByCreatedAtDesc(eq(Alert.AlertStatus.triggered), any()))
                .thenReturn(List.of());

        alertEscalationJob.escalateUnacknowledgedAlerts();

        verify(auditRepo, never()).save(any());
        verify(notificationClient, never()).sendNotification(
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test @Order(31)
    void escalation_OldCriticalAlertEscalated() {
        Alert old = Alert.builder()
                .id(UUID.randomUUID())
                .title("Critical: DB connection pool exhausted")
                .severity(Alert.AlertSeverity.critical)
                .status(Alert.AlertStatus.triggered)
                .createdAt(OffsetDateTime.now().minusMinutes(20))
                .build();

        when(alertRepo.findByStatusOrderByCreatedAtDesc(eq(Alert.AlertStatus.triggered), any()))
                .thenReturn(List.of(old));

        alertEscalationJob.escalateUnacknowledgedAlerts();

        // Audit log for escalation
        verify(auditRepo, times(1)).save(argThat(al ->
                "alert.escalated".equals(al.getAction())));

        // Notification for escalation
        verify(notificationClient, times(1)).sendNotification(
                eq("alert.escalated"),
                contains("ESCALATION"),
                anyString(),
                eq("critical"),
                any(), any());
    }

    @Test @Order(32)
    void escalation_RecentAlertNotEscalated() {
        Alert recent = Alert.builder()
                .id(UUID.randomUUID())
                .title("Warning: high latency")
                .severity(Alert.AlertSeverity.error)
                .status(Alert.AlertStatus.triggered)
                .createdAt(OffsetDateTime.now().minusMinutes(5)) // only 5 min old
                .build();

        when(alertRepo.findByStatusOrderByCreatedAtDesc(eq(Alert.AlertStatus.triggered), any()))
                .thenReturn(List.of(recent));

        alertEscalationJob.escalateUnacknowledgedAlerts();

        verify(auditRepo, never()).save(any());
        verify(notificationClient, never()).sendNotification(
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test @Order(33)
    void escalation_InfoAlertNotEscalated() {
        Alert info = Alert.builder()
                .id(UUID.randomUUID())
                .title("Info: scheduled maintenance")
                .severity(Alert.AlertSeverity.info)
                .status(Alert.AlertStatus.triggered)
                .createdAt(OffsetDateTime.now().minusMinutes(30))
                .build();

        when(alertRepo.findByStatusOrderByCreatedAtDesc(eq(Alert.AlertStatus.triggered), any()))
                .thenReturn(List.of(info));

        alertEscalationJob.escalateUnacknowledgedAlerts();

        verify(auditRepo, never()).save(any());
    }
}
