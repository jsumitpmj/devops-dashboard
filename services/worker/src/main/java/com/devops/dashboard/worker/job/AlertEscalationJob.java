package com.devops.dashboard.worker.job;

import com.devops.dashboard.shared.model.Alert;
import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.repository.AlertRepository;
import com.devops.dashboard.shared.repository.AuditLogRepository;
import com.devops.dashboard.worker.client.NotificationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors unacknowledged critical/error alerts.
 *
 * <p>If an alert has been in {@code triggered} status for over 15 minutes
 * without being acknowledged, this job escalates it by sending a reminder
 * notification.
 */
@Component
public class AlertEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(AlertEscalationJob.class);
    private static final int ESCALATION_THRESHOLD_MINUTES = 15;

    private final AlertRepository alertRepo;
    private final AuditLogRepository auditRepo;
    private final NotificationClient notificationClient;
    private final AtomicLong runCount = new AtomicLong(0);

    public AlertEscalationJob(AlertRepository alertRepo,
                               AuditLogRepository auditRepo,
                               NotificationClient notificationClient) {
        this.alertRepo = alertRepo;
        this.auditRepo = auditRepo;
        this.notificationClient = notificationClient;
    }

    @Scheduled(fixedDelay = 120_000) // every 2 minutes
    @Transactional
    public void escalateUnacknowledgedAlerts() {
        long count = runCount.incrementAndGet();
        log.debug("Alert escalation #{} starting", count);

        try {
            // Find all triggered alerts
            List<Alert> triggered = alertRepo.findByStatusOrderByCreatedAtDesc(
                    Alert.AlertStatus.triggered, PageRequest.of(0, 100));

            OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(ESCALATION_THRESHOLD_MINUTES);
            int escalated = 0;

            for (Alert alert : triggered) {
                // Only escalate critical/error alerts older than threshold
                if (alert.getCreatedAt().isBefore(threshold)
                        && (alert.getSeverity() == Alert.AlertSeverity.critical
                            || alert.getSeverity() == Alert.AlertSeverity.error)) {

                    log.warn("Escalating alert {}: '{}' (unacknowledged for >{}min)",
                            alert.getId(), alert.getTitle(), ESCALATION_THRESHOLD_MINUTES);

                    // Audit the escalation
                    auditRepo.save(AuditLog.builder()
                            .action("alert.escalated")
                            .entityType("alert")
                            .entityId(alert.getId())
                            .diff(Map.of(
                                    "reason", "unacknowledged >" + ESCALATION_THRESHOLD_MINUTES + " minutes",
                                    "severity", alert.getSeverity().name()))
                            .build());

                    // Notify
                    notificationClient.sendNotification(
                            "alert.escalated",
                            "ESCALATION: " + alert.getTitle(),
                            "Alert has been unacknowledged for over " + ESCALATION_THRESHOLD_MINUTES + " minutes.",
                            "critical",
                            alert.getProject() != null ? alert.getProject().getId() : null,
                            alert.getDeployment() != null ? alert.getDeployment().getId() : null);

                    escalated++;
                }
            }

            if (escalated > 0) {
                log.info("Escalation #{}: {} alerts escalated", count, escalated);
            } else {
                log.debug("Escalation #{}: no alerts to escalate", count);
            }
        } catch (Exception e) {
            log.error("Escalation #{} failed", count, e);
        }
    }
}
