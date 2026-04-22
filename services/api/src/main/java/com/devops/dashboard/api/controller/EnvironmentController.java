package com.devops.dashboard.api.controller;

import com.devops.dashboard.shared.model.Environment;
import com.devops.dashboard.shared.model.Project;
import com.devops.dashboard.shared.repository.EnvironmentRepository;
import com.devops.dashboard.shared.repository.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/environments")
public class EnvironmentController {

    private final EnvironmentRepository envRepo;
    private final ProjectRepository projectRepo;

    public EnvironmentController(EnvironmentRepository envRepo, ProjectRepository projectRepo) {
        this.envRepo = envRepo;
        this.projectRepo = projectRepo;
    }

    @GetMapping
    public ResponseEntity<?> listByProject(@RequestParam UUID projectId) {
        List<Environment> envs = envRepo.findByProjectIdOrderByName(projectId);
        return ResponseEntity.ok(Map.of("data", envs, "total", envs.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        ResponseEntity<?> notFound = ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Environment not found"));
        return envRepo.findById(id)
                .<ResponseEntity<?>>map(e -> ResponseEntity.ok(Map.of("data", e)))
                .orElse(notFound);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','developer')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateEnvironmentRequest req) {
        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Environment env = Environment.builder()
                .project(project)
                .name(req.getName())
                .envType(Environment.EnvironmentType.valueOf(req.getEnvType()))
                .url(req.getUrl())
                .build();

        env = envRepo.save(env);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", env));
    }

    @Data
    public static class CreateEnvironmentRequest {
        @NotNull(message = "Project ID is required")
        private UUID projectId;
        @NotBlank(message = "Name is required")
        @Size(max = 128)
        private String name;
        @NotBlank(message = "Environment type is required")
        private String envType;
        private String url;
    }
}
