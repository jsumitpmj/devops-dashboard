package com.devops.dashboard.notification.handler;

import com.devops.dashboard.notification.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Slack notification handler.
 *
 * <p>In production this would POST a JSON payload to a Slack Incoming
 * Webhook URL. For now it logs the message so the full pipeline
 * is exercised.
 */
@Component
public class SlackNotificationHandler implements NotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationHandler.class);

    @Value("${notification.slack.enabled:false}")
    private boolean enabled;

    @Value("${notification.slack.webhook-url:}")
    private String webhookUrl;

    @Override
    public String channel() {
        return "slack";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String send(NotificationEvent event) {
        String slackChannel = event.getMetadata() != null
                ? event.getMetadata().getOrDefault("slack_channel", "#deployments")
                : "#deployments";

        String severityEmoji = switch (event.getSeverity() != null ? event.getSeverity() : "info") {
            case "critical" -> "🔴";
            case "error" -> "❌";
            case "warning" -> "⚠️";
            default -> "ℹ️";
        };

        String payload = String.format("%s *%s*\n%s\n_Channel: %s_",
                severityEmoji, event.getTitle(),
                event.getMessage() != null ? event.getMessage() : "",
                slackChannel);

        // In production: HttpClient.newHttpClient().send(POST webhookUrl, payload)
        log.info("[SLACK] channel={}, webhook={}, message='{}'",
                slackChannel, webhookUrl.isEmpty() ? "(not configured)" : "***", event.getTitle());
        log.debug("[SLACK] payload: {}", payload);

        return String.format("Slack posted: channel=%s, title='%s'", slackChannel, event.getTitle());
    }
}
