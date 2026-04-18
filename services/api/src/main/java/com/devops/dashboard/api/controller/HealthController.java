package com.devops.dashboard.api.controller;

import com.devops.dashboard.shared.dto.HealthResponse;
import com.devops.dashboard.shared.health.HealthChecker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final HealthChecker healthChecker;

    public HealthController(HealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthChecker.check();
        int status = "ok".equals(response.getStatus()) ? 200 : 503;
        return ResponseEntity.status(status).body(response);
    }
}
