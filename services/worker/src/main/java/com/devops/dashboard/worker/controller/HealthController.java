package com.devops.dashboard.worker.controller;

import com.devops.dashboard.shared.dto.HealthResponse;
import com.devops.dashboard.shared.health.HealthChecker;
import com.devops.dashboard.shared.repository.AlertRepository;
import com.devops.dashboard.shared.repository.DeploymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final HealthChecker healthChecker;
    private final DeploymentRepository deployRepo;
    private final AlertRepository alertRepo;

    public HealthController(HealthChecker healthChecker,
                            DeploymentRepository deployRepo,
                            AlertRepository alertRepo) {
        this.healthChecker = healthChecker;
        this.deployRepo = deployRepo;
        this.alertRepo = alertRepo;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthChecker.check();
        int status = "ok".equals(response.getStatus()) ? 200 : 503;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Returns current job queue stats — useful for monitoring dashboards.
     */
    @GetMapping("/worker/stats")
    public ResponseEntity<?> stats() {
        long pending = deployRepo.countPending();
        long totalDeploys = deployRepo.count();
        long totalAlerts = alertRepo.count();

        return ResponseEntity.ok(Map.of(
                "pendingDeployments", pending,
                "totalDeployments", totalDeploys,
                "totalAlerts", totalAlerts));
    }
}
