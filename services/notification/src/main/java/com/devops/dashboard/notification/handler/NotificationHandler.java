package com.devops.dashboard.notification.handler;

import com.devops.dashboard.notification.dto.NotificationEvent;

/**
 * Strategy interface for notification delivery channels.
 * Each channel (email, slack, webhook, etc.) implements this
 * and is auto-discovered via Spring component scanning.
 */
public interface NotificationHandler {

    /** The channel name this handler supports (e.g. "email", "slack"). */
    String channel();

    /** Whether this handler is currently enabled (from config). */
    boolean isEnabled();

    /**
     * Deliver the notification.
     *
     * @return a human-readable summary of what was done
     */
    String send(NotificationEvent event);
}
