package com.devops.dashboard.deployment.service;

import com.devops.dashboard.deployment.dto.CreateDeploymentRequest;
import com.devops.dashboard.deployment.dto.UpdateStatusRequest;
import com.devops.dashboard.shared.model.*;
import com.devops.dashboard.shared.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private final DeploymentRepository deployRepo;
    private final EnvironmentRepository envRepo;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;

    public DeploymentService(DeploymentRepository deployRepo,
                             EnvironmentRepository envRepo,
                             ProjectRepository projectRepo,
                             UserRepository userRepo,
                             AuditLogRepository auditRepo) {
        this.deployRepo = deployRepo;
        this.envRepo = envRepo;
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
        this.auditRepo = auditRepo;
    }

    // ---- Create ----

    @Transactional
    public Deployment create(CreateDeploymentRequest req, UUID triggeredById) {
        Environment env = envRepo.findById(req.getEnvironmentId())
                .orElseThrow(() -> new IllegalArgumentException("Environment not found"));

        if (env.getIsLocked()) {
            throw new IllegalStateException("Environment '" + env.getName() + "' is locked — deployments are blocked");
        }

        Project project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        User user = userRepo.findById(triggeredById)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Deployment deployment = Deployment.builder()
                .project(project)
                .environment(env)
                .triggeredBy(user)
                .gitRef(req.getGitRef())
                .gitSha(req.getGitSha())
                .imageTag(req.getImageTag())
                .build();

        deployment = deployRepo.save(deployment);

        auditRepo.save(AuditLog.builder()
                .actorId(triggeredById)
                .action("deployment.created")
                .entityType("deployment")
                .entityId(deployment.getId())
                .diff(Map.of("after", Map.of(
                        "status", "pending",
                        "gitRef", req.getGitRef() != null ? req.getGitRef() : "",
                        "environmentId", req.getEnvironmentId().toString())))
                .build());

        log.info("Deployment created: id={}, project={}, env={}",
                deployment.getId(), project.getSlug(), env.getName());
        return deployment;
    }

    // ---- Update status ----

    @Transactional
    public Deployment updateStatus(UUID deploymentId, UpdateStatusRequest req, UUID actorId) {
        Deployment deployment = deployRepo.findById(deploymentId)
                .orElseThrow(() -> new IllegalArgumentException("Deployment not found"));

        String oldStatus = deployment.getStatus().name();
        Deployment.DeploymentStatus newStatus = Deployment.DeploymentStatus.valueOf(req.getStatus());
        deployment.setStatus(newStatus);

        // Lifecycle timestamps
        if (newStatus == Deployment.DeploymentStatus.in_progress && deployment.getStartedAt() == null) {
            deployment.setStartedAt(OffsetDateTime.now());
        }
        if (isTerminal(newStatus)) {
            deployment.setFinishedAt(OffsetDateTime.now());
            if (deployment.getStartedAt() != null) {
                long durationMs = java.time.Duration.between(
                        deployment.getStartedAt(), deployment.getFinishedAt()).toMillis();
                deployment.setDurationMs((int) durationMs);
            }
        }
        if (newStatus == Deployment.DeploymentStatus.failed && req.getErrorMessage() != null) {
            deployment.setErrorMessage(req.getErrorMessage());
        }

        deployment = deployRepo.save(deployment);

        // Audit
        String action = "deployment." + newStatus.name();
        auditRepo.save(AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .entityType("deployment")
                .entityId(deploymentId)
                .diff(Map.of(
                        "before", Map.of("status", oldStatus),
                        "after", Map.of("status", newStatus.name())))
                .build());

        log.info("Deployment {} status: {} → {}", deploymentId, oldStatus, newStatus);
        return deployment;
    }

    // ---- Reads ----

    public List<Deployment> listByProject(UUID projectId, int page, int size) {
        return deployRepo.findByProjectIdOrderByCreatedAtDesc(
                projectId, PageRequest.of(page, Math.min(size, 100)));
    }

    public List<Deployment> listByEnvironment(UUID envId, int page, int size) {
        return deployRepo.findByEnvironmentIdOrderByCreatedAtDesc(
                envId, PageRequest.of(page, Math.min(size, 100)));
    }

    public List<Deployment> listByStatus(String status, int page, int size) {
        return deployRepo.findByStatusOrderByCreatedAtDesc(
                Deployment.DeploymentStatus.valueOf(status),
                PageRequest.of(page, Math.min(size, 100)));
    }

    private boolean isTerminal(Deployment.DeploymentStatus s) {
        return s == Deployment.DeploymentStatus.succeeded
                || s == Deployment.DeploymentStatus.failed
                || s == Deployment.DeploymentStatus.rolled_back
                || s == Deployment.DeploymentStatus.cancelled;
    }
}
