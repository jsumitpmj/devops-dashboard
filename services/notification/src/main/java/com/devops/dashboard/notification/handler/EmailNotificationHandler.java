package com.devops.dashboard.notification.handler;

import com.devops.dashboard.notification.dto.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Email notification handler.
 *
 * <p>In a production deployment this would use JavaMail / Spring Mail
 * to deliver via SMTP. For now it logs the payload so the pipeline
 * is fully exercised end-to-end.
 */
@Component
public class EmailNotificationHandler implements NotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationHandler.class);

    @Value("${notification.email.enabled:false}")
    private boolean enabled;

    @Value("${notification.email.from:noreply@devops-dashboard.local}")
    private String fromAddress;

    @Value("${notification.email.smtp-host:localhost}")
    private String smtpHost;

    @Value("${notification.email.smtp-port:587}")
    private int smtpPort;

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String send(NotificationEvent event) {
        String recipient = event.getMetadata() != null
                ? event.getMetadata().getOrDefault("recipient", "admin@devops-dashboard.local")
                : "admin@devops-dashboard.local";

        // In production: construct MimeMessage, connect to smtpHost:smtpPort, send.
        log.info("[EMAIL] from={}, to={}, subject='{}', smtpHost={}:{}",
                fromAddress, recipient, event.getTitle(), smtpHost, smtpPort);
        log.debug("[EMAIL] body: {}", event.getMessage());

        return String.format("Email queued: to=%s, subject='%s'", recipient, event.getTitle());
    }
}
