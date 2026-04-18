package com.devops.dashboard.deployment.controller;

import com.devops.dashboard.deployment.dto.CreateDeploymentRequest;
import com.devops.dashboard.deployment.dto.UpdateStatusRequest;
import com.devops.dashboard.deployment.service.DeploymentService;
import com.devops.dashboard.shared.dto.HealthResponse;
import com.devops.dashboard.shared.health.HealthChecker;
import com.devops.dashboard.shared.model.Deployment;
import com.devops.dashboard.shared.repository.DeploymentRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class DeploymentController {

    private final DeploymentService deployService;
    private final DeploymentRepository deployRepo;
    private final HealthChecker healthChecker;

    public DeploymentController(DeploymentService deployService,
                                DeploymentRepository deployRepo,
                                HealthChecker healthChecker) {
        this.deployService = deployService;
        this.deployRepo = deployRepo;
        this.healthChecker = healthChecker;
    }

    // ---- Health ----

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = healthChecker.check();
        int status = "ok".equals(response.getStatus()) ? 200 : 503;
        return ResponseEntity.status(status).body(response);
    }

    // ---- List deployments ----

    @GetMapping("/api/v1/deployments")
    public ResponseEntity<?> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<Deployment> deployments;

        if (projectId != null) {
            deployments = deployService.listByProject(projectId, page, size);
        } else if (environmentId != null) {
            deployments = deployService.listByEnvironment(environmentId, page, size);
        } else if (status != null) {
            deployments = deployService.listByStatus(status, page, size);
        } else {
            deployments = deployRepo.findAll(PageRequest.of(page, Math.min(size, 100))).getContent();
        }

        return ResponseEntity.ok(Map.of(
                "data", deployments, "total", deployments.size(),
                "page", page, "pageSize", size));
    }

    // ---- Get by ID ----

    @GetMapping("/api/v1/deployments/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        return deployRepo.findById(id)
                .map(d -> ResponseEntity.ok(Map.of("data", d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Deployment not found")));
    }

    // ---- Create deployment ----

    @PostMapping("/api/v1/deployments")
    @PreAuthorize("hasAnyAuthority('admin','developer','deployer')")
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateDeploymentRequest req,
            Authentication auth) {
        UUID triggeredBy = UUID.fromString((String) auth.getPrincipal());
        Deployment deployment = deployService.create(req, triggeredBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", deployment));
    }

    // ---- Update status ----

    @PutMapping("/api/v1/deployments/{id}/status")
    @PreAuthorize("hasAnyAuthority('admin','developer','deployer')")
    public ResponseEntity<?> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req,
            Authentication auth) {
        UUID actorId = UUID.fromString((String) auth.getPrincipal());
        Deployment deployment = deployService.updateStatus(id, req, actorId);
        return ResponseEntity.ok(Map.of("data", deployment));
    }

    // ---- Deployment stats ----

    @GetMapping("/api/v1/deployments/stats")
    public ResponseEntity<?> stats() {
        long pending = deployRepo.countPending();
        long total = deployRepo.count();
        return ResponseEntity.ok(Map.of("pending", pending, "total", total));
    }
}
