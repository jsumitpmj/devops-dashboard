package com.devops.dashboard.api.controller;

import com.devops.dashboard.shared.model.Deployment;
import com.devops.dashboard.shared.repository.DeploymentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only deployment endpoints for the API gateway.
 * Write operations are handled by the Deployment service.
 */
@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentReadController {

    private final DeploymentRepository deployRepo;

    public DeploymentReadController(DeploymentRepository deployRepo) {
        this.deployRepo = deployRepo;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        List<Deployment> deployments;

        if (projectId != null && status != null) {
            deployments = deployRepo.findByProjectIdAndStatusOrderByCreatedAtDesc(
                    projectId, Deployment.DeploymentStatus.valueOf(status), pageable);
        } else if (projectId != null) {
            deployments = deployRepo.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
        } else if (environmentId != null) {
            deployments = deployRepo.findByEnvironmentIdOrderByCreatedAtDesc(environmentId, pageable);
        } else if (status != null) {
            deployments = deployRepo.findByStatusOrderByCreatedAtDesc(
                    Deployment.DeploymentStatus.valueOf(status), pageable);
        } else {
            deployments = deployRepo.findAll(pageable).getContent();
        }

        return ResponseEntity.ok(Map.of("data", deployments, "total", deployments.size(),
                "page", page, "pageSize", size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        return deployRepo.findById(id)
                .map(d -> ResponseEntity.ok(Map.of("data", d)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Deployment not found")));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        long pending = deployRepo.countPending();
        long total = deployRepo.count();
        return ResponseEntity.ok(Map.of("pending", pending, "total", total));
    }
}
