package com.devops.dashboard.worker.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Lightweight REST client that calls the Notification service
 * to fire alerts when important events happen in the worker.
 *
 * <p>Uses Spring 6's {@link RestClient} (introduced in Spring Framework 6.1).
 * If the notification service is down, errors are logged but not propagated.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient restClient;

    public NotificationClient(
            @Value("${worker.notification-service-url:http://localhost:4003}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Send a notification event to the notification service.
     */
    public void sendNotification(String eventType,
                                  String title,
                                  String message,
                                  String severity,
                                  UUID projectId,
                                  UUID deploymentId) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("eventType", eventType);
            body.put("title", title);
            body.put("message", message != null ? message : "");
            body.put("channels", new String[]{"email", "slack"});
            body.put("severity", severity != null ? severity : "info");
            if (projectId != null) body.put("projectId", projectId.toString());
            if (deploymentId != null) body.put("deploymentId", deploymentId.toString());

            restClient.post()
                    .uri("/api/v1/notifications/send-async")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Notification sent: eventType={}, title='{}'", eventType, title);
        } catch (Exception ex) {
            log.warn("Failed to send notification (service may be down): {}", ex.getMessage());
        }
    }
}
