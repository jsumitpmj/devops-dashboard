package com.devops.dashboard.api.controller;

import com.devops.dashboard.shared.dto.ApiError;
import com.devops.dashboard.shared.model.Project;
import com.devops.dashboard.shared.model.User;
import com.devops.dashboard.shared.repository.ProjectRepository;
import com.devops.dashboard.shared.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;

    public ProjectController(ProjectRepository projectRepo, UserRepository userRepo) {
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<Project> projects = projectRepo.findByIsArchivedFalseOrderByCreatedAtDesc();
        return ResponseEntity.ok(Map.of("data", projects, "total", projects.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        return projectRepo.findById(id)
                .map(p -> ResponseEntity.ok(Map.of("data", p)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("error", ApiError.builder().error("Not Found")
                                .message("Project not found").statusCode(404).build())));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<?> getBySlug(@PathVariable String slug) {
        return projectRepo.findBySlug(slug)
                .map(p -> ResponseEntity.ok(Map.of("data", p)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("error", ApiError.builder().error("Not Found")
                                .message("Project not found").statusCode(404).build())));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('admin','developer')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateProjectRequest req, Authentication auth) {
        if (projectRepo.findBySlug(req.getSlug()).isPresent()) {
            throw new IllegalArgumentException("A project with slug '" + req.getSlug() + "' already exists");
        }

        UUID ownerId = UUID.fromString((String) auth.getPrincipal());
        User owner = userRepo.findById(ownerId).orElseThrow(
                () -> new IllegalArgumentException("Owner user not found"));

        Project project = Project.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .repoUrl(req.getRepoUrl())
                .owner(owner)
                .build();

        project = projectRepo.save(project);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", project));
    }

    @Data
    public static class CreateProjectRequest {
        @NotBlank(message = "Name is required")
        private String name;
        @NotBlank(message = "Slug is required")
        @Size(max = 128)
        private String slug;
        private String description;
        private String repoUrl;
    }
}
