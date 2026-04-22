package com.devops.dashboard.notification.service;

import com.devops.dashboard.notification.dto.NotificationEvent;
import com.devops.dashboard.notification.dto.NotificationResult;
import com.devops.dashboard.notification.handler.NotificationHandler;
import com.devops.dashboard.shared.model.Alert;
import com.devops.dashboard.shared.model.AuditLog;
import com.devops.dashboard.shared.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Orchestrates notification delivery across all registered channels.
 *
 * <p>Flow:
 * <ol>
 *   <li>Persist an {@link Alert} record for auditing / dashboard display.</li>
 *   <li>Fan-out to every requested channel via the {@link NotificationHandler} strategy.</li>
 *   <li>Log an audit entry for the send attempt.</li>
 * </ol>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Map<String, NotificationHandler> handlers = new LinkedHashMap<>();
    private final AlertRepository alertRepo;
    private final AuditLogRepository auditRepo;
    private final ProjectRepository projectRepo;
    private final EnvironmentRepository envRepo;
    private final DeploymentRepository deployRepo;

    public NotificationService(List<NotificationHandler> handlerList,
                               AlertRepository alertRepo,
                               AuditLogRepository auditRepo,
                               ProjectRepository projectRepo,
                               EnvironmentRepository envRepo,
                               DeploymentRepository deployRepo) {
        for (NotificationHandler h : handlerList) {
            handlers.put(h.channel(), h);
            log.info("Registered notification handler: channel={}, enabled={}", h.channel(), h.isEnabled());
        }
        this.alertRepo = alertRepo;
        this.auditRepo = auditRepo;
        this.projectRepo = projectRepo;
        this.envRepo = envRepo;
        this.deployRepo = deployRepo;
    }

    /**
     * Process a notification event synchronously (called from the controller).
     */
    @Transactional
    public NotificationResult process(NotificationEvent event) {
        // 1) Persist an alert record
        Alert alert = persistAlert(event);

        // 2) Dispatch to each requested channel
        List<NotificationResult.ChannelResult> channelResults = new ArrayList<>();
        for (String channel : event.getChannels()) {
            channelResults.add(dispatch(channel, event));
        }

        // 3) Audit trail
        auditRepo.save(AuditLog.builder()
                .action("notification.sent")
                .entityType("alert")
                .entityId(alert.getId())
                .diff(Map.of(
                        "eventType", event.getEventType(),
                        "channels", Arrays.asList(event.getChannels()),
                        "results", channelResults.stream()
                                .map(r -> r.getChannel() + ":" + r.isSent())
                                .toList()))
                .build());

        log.info("Notification processed: eventType={}, alertId={}, channels={}",
                event.getEventType(), alert.getId(), Arrays.asList(event.getChannels()));

        return NotificationResult.builder()
                .alertId(alert.getId())
                .eventType(event.getEventType())
                .channelResults(channelResults)
                .processedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Fire-and-forget async variant — used when a caller doesn't need the result.
     */
    @Async
    public void processAsync(NotificationEvent event) {
        try {
            process(event);
        } catch (Exception ex) {
            log.error("Async notification failed for eventType={}", event.getEventType(), ex);
        }
    }

    /**
     * Returns the names of all registered channels and their enabled status.
     */
    public Map<String, Boolean> listChannels() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        handlers.forEach((name, handler) -> result.put(name, handler.isEnabled()));
        return result;
    }

    // ---- internals ----------------------------------------------------------

    private Alert persistAlert(NotificationEvent event) {
        var builder = Alert.builder()
                .title(event.getTitle())
                .message(event.getMessage())
                .severity(parseSeverity(event.getSeverity()))
                .source("notification-" + event.getEventType())
                .metadata(event.getMetadata() != null
                        ? new HashMap<>(event.getMetadata())
                        : Map.of());

        // Link optional entity references
        if (event.getProjectId() != null) {
            projectRepo.findById(event.getProjectId()).ifPresent(builder::project);
        }
        if (event.getEnvironmentId() != null) {
            envRepo.findById(event.getEnvironmentId()).ifPresent(builder::environment);
        }
        if (event.getDeploymentId() != null) {
            deployRepo.findById(event.getDeploymentId()).ifPresent(builder::deployment);
        }

        return alertRepo.save(builder.build());
    }

    private NotificationResult.ChannelResult dispatch(String channel, NotificationEvent event) {
        NotificationHandler handler = handlers.get(channel);

        if (handler == null) {
            log.warn("No handler registered for channel '{}'", channel);
            return NotificationResult.ChannelResult.builder()
                    .channel(channel).sent(false).detail("Unknown channel").build();
        }

        if (!handler.isEnabled()) {
            log.info("Channel '{}' is disabled — skipping", channel);
            return NotificationResult.ChannelResult.builder()
                    .channel(channel).sent(false).detail("Channel disabled").build();
        }

        try {
            String detail = handler.send(event);
            return NotificationResult.ChannelResult.builder()
                    .channel(channel).sent(true).detail(detail).build();
        } catch (Exception ex) {
            log.error("Channel '{}' failed for event '{}'", channel, event.getEventType(), ex);
            return NotificationResult.ChannelResult.builder()
                    .channel(channel).sent(false).detail("Error: " + ex.getMessage()).build();
        }
    }

    private Alert.AlertSeverity parseSeverity(String s) {
        try {
            return s != null ? Alert.AlertSeverity.valueOf(s) : Alert.AlertSeverity.info;
        } catch (IllegalArgumentException e) {
            return Alert.AlertSeverity.info;
        }
    }
}
