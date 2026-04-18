package com.devops.dashboard.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Represents an internal event that should trigger a notification.
 * Typically sent by other services (deployment, worker) via REST.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationEvent {

    @NotBlank(message = "Event type is required")
    private String eventType;       // e.g. deployment.succeeded, deployment.failed, alert.critical

    @NotBlank(message = "Title is required")
    private String title;

    private String message;

    @NotNull(message = "At least one channel is required")
    private String[] channels;      // e.g. ["email", "slack"]

    private String severity;        // info, warning, error, critical

    // Optional entity references for linking the alert record
    private UUID projectId;
    private UUID environmentId;
    private UUID deploymentId;

    // Freeform data (recipient email, slack channel, etc.)
    private Map<String, String> metadata;
}
