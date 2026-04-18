package com.devops.dashboard.worker.job;

import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.repository.AlertRepository;
import com.devops.dashboard.shared.repository.AuditLogRepository;
import com.devops.dashboard.shared.repository.DeploymentRepository;
import com.devops.dashboard.shared.repository.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates a background sync task that reconciles environment health.
 *
 * <p>In a real system this would poll Kubernetes clusters, cloud APIs,
 * or health-check URLs. Here it demonstrates the pattern by logging
 * sync activity and counting aggregate stats.
 */
@Component
public class EnvironmentSyncJob {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentSyncJob.class);

    private final EnvironmentRepository envRepo;
    private final DeploymentRepository deployRepo;
    private final AlertRepository alertRepo;
    private final AuditLogRepository auditRepo;
    private final AtomicLong syncCount = new AtomicLong(0);

    public EnvironmentSyncJob(EnvironmentRepository envRepo,
                               DeploymentRepository deployRepo,
                               AlertRepository alertRepo,
                               AuditLogRepository auditRepo) {
        this.envRepo = envRepo;
        this.deployRepo = deployRepo;
        this.alertRepo = alertRepo;
        this.auditRepo = auditRepo;
    }

    @Scheduled(fixedDelayString = "${worker.sync-interval-ms:60000}")
    @Transactional
    public void syncEnvironments() {
        long count = syncCount.incrementAndGet();
        log.debug("Environment sync #{} starting", count);

        try {
            long totalEnvs = envRepo.count();
            long totalDeploys = deployRepo.count();
            long pendingDeploys = deployRepo.countPending();

            // Simulate health check — in production this would ping each env URL
            log.info("Sync #{}: environments={}, deployments={}, pending={}",
                    count, totalEnvs, totalDeploys, pendingDeploys);

            // Audit periodic sync
            if (count % 10 == 0) {
                auditRepo.save(AuditLog.builder()
                        .action("worker.sync_checkpoint")
                        .entityType("system")
                        .diff(Map.of(
                                "syncCount", count,
                                "environments", totalEnvs,
                                "deployments", totalDeploys,
                                "pendingDeployments", pendingDeploys))
                        .build());
                log.info("Sync checkpoint #{} saved to audit log", count);
            }
        } catch (Exception e) {
            log.error("Sync #{} failed", count, e);
        }
    }
}
