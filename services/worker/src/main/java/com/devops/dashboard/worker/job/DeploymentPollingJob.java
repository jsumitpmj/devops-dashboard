package com.devops.dashboard.worker.job;

import com.devops.dashboard.shared.model.Alert;
import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.model.Deployment;
import com.devops.dashboard.shared.repository.AlertRepository;
import com.devops.dashboard.shared.repository.AuditLogRepository;
import com.devops.dashboard.shared.repository.DeploymentRepository;
import com.devops.dashboard.worker.client.NotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Polls for stale deployments (stuck in {@code in_progress} beyond a
 * configurable timeout) and marks them as {@code failed}.
 *
 * <p>For each timed-out deployment:
 * <ol>
 *   <li>Updates the deployment status + finishedAt + durationMs.</li>
 *   <li>Creates a high-severity alert record.</li>
 *   <li>Writes an audit log entry.</li>
 *   <li>Fires a notification to the Notification service.</li>
 * </ol>
 */
@Component
public class DeploymentPollingJob {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPollingJob.class);

    private final DeploymentRepository deployRepo;
    private final AlertRepository alertRepo;
    private final AuditLogRepository auditRepo;
    private final NotificationClient notificationClient;
    private final AtomicLong pollCount = new AtomicLong(0);

    @Value("${worker.stale-timeout-minutes:30}")
    private int staleTimeoutMinutes;

    public DeploymentPollingJob(DeploymentRepository deployRepo,
                                AlertRepository alertRepo,
                                AuditLogRepository auditRepo,
                                NotificationClient notificationClient) {
        this.deployRepo = deployRepo;
        this.alertRepo = alertRepo;
        this.auditRepo = auditRepo;
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedDelayString = "${worker.poll-interval-ms:30000}")
    @Transactional
    public void pollDeployments() {
        long count = pollCount.incrementAndGet();
        log.debug("Deployment poll #{} starting", count);

        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(staleTimeoutMinutes);
            List<Deployment> stale = deployRepo.findStaleDeployments(cutoff, PageRequest.of(0, 100));

            for (Deployment dep : stale) {
                failDeployment(dep);
            }

            long pending = deployRepo.countPending();
            log.info("Poll #{}: stale={}, pending={}", count, stale.size(), pending);
        } catch (Exception e) {
            log.error("Poll #{} failed", count, e);
        }
    }

    private void failDeployment(Deployment dep) {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. Update deployment
        dep.setStatus(Deployment.DeploymentStatus.failed);
        dep.setErrorMessage("Timed out after " + staleTimeoutMinutes + " minutes");
        dep.setFinishedAt(now);
        if (dep.getStartedAt() != null) {
            dep.setDurationMs((int) Duration.between(dep.getStartedAt(), now).toMillis());
        }
        deployRepo.save(dep);

        // 2. Alert
        alertRepo.save(Alert.builder()
                .title("Deployment timed out")
                .message("Deployment " + dep.getId() + " exceeded " + staleTimeoutMinutes + "-minute timeout")
                .severity(Alert.AlertSeverity.error)
                .source("worker-timeout")
                .deployment(dep)
                .environment(dep.getEnvironment())
                .project(dep.getProject())
                .metadata(Map.of("timeout_minutes", String.valueOf(staleTimeoutMinutes)))
                .build());

        // 3. Audit
        auditRepo.save(AuditLog.builder()
                .action("deployment.timed_out")
                .entityType("deployment")
                .entityId(dep.getId())
                .diff(Map.of(
                        "reason", "timeout",
                        "before", Map.of("status", "in_progress"),
                        "after", Map.of("status", "failed")))
                .build());

        // 4. Notify (fire-and-forget to notification service)
        notificationClient.sendNotification(
                "deployment.timed_out",
                "Deployment timed out: " + dep.getId(),
                "Deployment exceeded " + staleTimeoutMinutes + "-minute timeout and was marked as failed.",
                "error",
                dep.getProject() != null ? dep.getProject().getId() : null,
                dep.getId());

        log.warn("Deployment {} marked as FAILED (timeout)", dep.getId());
    }
}
