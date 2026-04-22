package com.devops.dashboard.api.controller;

import com.devops.dashboard.shared.model.Alert;
import com.devops.dashboard.shared.repository.AlertRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertRepository alertRepo;

    public AlertController(AlertRepository alertRepo) {
        this.alertRepo = alertRepo;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID deploymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        List<Alert> alerts;

        if (deploymentId != null) {
            alerts = alertRepo.findByDeploymentIdOrderByCreatedAtDesc(deploymentId);
        } else if (projectId != null) {
            alerts = alertRepo.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
        } else if (status != null) {
            alerts = alertRepo.findByStatusOrderByCreatedAtDesc(
                    Alert.AlertStatus.valueOf(status), pageable);
        } else {
            alerts = alertRepo.findAllByOrderByCreatedAtDesc(pageable);
        }

        return ResponseEntity.ok(Map.of("data", alerts, "total", alerts.size(),
                "page", page, "pageSize", size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        ResponseEntity<?> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Alert not found"));
        return alertRepo.findById(id)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of("data", a)))
                .orElse(notFound);
    }
}
