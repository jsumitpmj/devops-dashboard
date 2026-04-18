package com.devops.dashboard.notification;

import com.devops.dashboard.notification.dto.NotificationEvent;
import com.devops.dashboard.notification.dto.NotificationResult;
import com.devops.dashboard.notification.handler.NotificationHandler;
import com.devops.dashboard.notification.service.NotificationService;
import com.devops.dashboard.shared.model.Alert;
import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationService notificationService;

    @MockBean private AlertRepository alertRepo;
    @MockBean private AuditLogRepository auditRepo;
    @MockBean private ProjectRepository projectRepo;
    @MockBean private EnvironmentRepository envRepo;
    @MockBean private DeploymentRepository deployRepo;
    @MockBean private UserRepository userRepo;
    @MockBean private RoleRepository roleRepo;

    @BeforeEach
    void setUp() {
        reset(alertRepo, auditRepo);
        when(alertRepo.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- Health ----

    @Test @Order(1)
    void health_ReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("notification"));
    }

    // ---- Send notification ----

    @Test @Order(10)
    void send_Success() throws Exception {
        NotificationEvent event = NotificationEvent.builder()
                .eventType("deployment.succeeded")
                .title("Deploy to prod succeeded")
                .message("v2.1.0 deployed to production")
                .channels(new String[]{"email", "slack"})
                .severity("info")
                .build();

        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertId").exists())
                .andExpect(jsonPath("$.eventType").value("deployment.succeeded"))
                .andExpect(jsonPath("$.channelResults").isArray())
                .andExpect(jsonPath("$.channelResults.length()").value(2));

        verify(alertRepo, times(1)).save(any(Alert.class));
        verify(auditRepo, times(1)).save(any(AuditLog.class));
    }

    @Test @Order(11)
    void send_UnknownChannel() throws Exception {
        NotificationEvent event = NotificationEvent.builder()
                .eventType("test.event")
                .title("Test")
                .channels(new String[]{"sms"})
                .build();

        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channelResults[0].channel").value("sms"))
                .andExpect(jsonPath("$.channelResults[0].sent").value(false))
                .andExpect(jsonPath("$.channelResults[0].detail").value("Unknown channel"));
    }

    @Test @Order(12)
    void send_ValidationFailure() throws Exception {
        // Missing required fields
        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- Async send ----

    @Test @Order(20)
    void sendAsync_Accepted() throws Exception {
        NotificationEvent event = NotificationEvent.builder()
                .eventType("deployment.failed")
                .title("Deploy failed")
                .channels(new String[]{"email"})
                .severity("error")
                .build();

        mockMvc.perform(post("/api/v1/notifications/send-async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
    }

    // ---- Channels ----

    @Test @Order(30)
    void channels_ListsRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.email").exists())
                .andExpect(jsonPath("$.channels.slack").exists());
    }

    // ---- Service unit test ----

    @Test @Order(40)
    void service_ProcessSetsAlertIdAndChannelResults() {
        NotificationEvent event = NotificationEvent.builder()
                .eventType("alert.test")
                .title("Unit test alert")
                .channels(new String[]{"email", "slack"})
                .severity("warning")
                .build();

        NotificationResult result = notificationService.process(event);

        assertThat(result.getAlertId()).isNotNull();
        assertThat(result.getEventType()).isEqualTo("alert.test");
        assertThat(result.getChannelResults()).hasSize(2);
        assertThat(result.getProcessedAt()).isNotNull();
    }

    @Test @Order(41)
    void service_ListChannelsReturnsAllHandlers() {
        Map<String, Boolean> channels = notificationService.listChannels();
        assertThat(channels).containsKeys("email", "slack");
    }
}
