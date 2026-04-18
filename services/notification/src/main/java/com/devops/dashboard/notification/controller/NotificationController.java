package com.devops.dashboard.notification.controller;

import com.devops.dashboard.notification.dto.NotificationEvent;
import com.devops.dashboard.notification.dto.NotificationResult;
import com.devops.dashboard.notification.service.NotificationService;
import com.devops.dashboard.shared.dto.HealthResponse;
import com.devops.dashboard.shared.health.HealthChecker;
import com.devops.dashboard.shared.model.Alert;
import com.devops.dashboard.shared.repository.AlertRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService notificationService;
    private final AlertRepository alertRepo;
    private final HealthChecker healthChecker;

    public NotificationController(NotificationService notificationService,
                                  AlertRepository alertRepo,
                                  HealthChecker healthChecker) {
        this.notificationService = notificationService;
        this.alertRepo = alertRepo;
        this.healthChecker = healthChecker;
    }

    // ---- Health ----

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthChecker.check();
        int status = "ok".equals(response.getStatus()) ? 200 : 503;
        return ResponseEntity.status(status).body(response);
    }

    // ---- Send notification (internal event intake) ----

    @PostMapping("/api/v1/notifications/send")
    public ResponseEntity<NotificationResult> send(@Valid @RequestBody NotificationEvent event) {
        NotificationResult result = notificationService.process(event);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    // ---- Fire-and-forget async send ----

    @PostMapping("/api/v1/notifications/send-async")
    public ResponseEntity<?> sendAsync(@Valid @RequestBody NotificationEvent event) {
        notificationService.processAsync(event);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Notification accepted for async processing",
                "eventType", event.getEventType()));
    }

    // ---- Channel info ----

    @GetMapping("/api/v1/notifications/channels")
    public ResponseEntity<?> channels() {
        return ResponseEntity.ok(Map.of("channels", notificationService.listChannels()));
    }

    // ---- Alert listing (for dashboard) ----

    @GetMapping("/api/v1/alerts")
    public ResponseEntity<?> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID deploymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        List<Alert> alerts;

        if (deploymentId != null) {
            alerts = alertRepo.findByDeploymentIdOrderByCreatedAtDesc(deploymentId);
        } else if (status != null) {
            alerts = alertRepo.findByStatusOrderByCreatedAtDesc(
                    Alert.AlertStatus.valueOf(status), pageable);
        } else {
            alerts = alertRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        return ResponseEntity.ok(Map.of("data", alerts, "total", alerts.size()));
    }
}
