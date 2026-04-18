package com.devops.dashboard.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResult {
    private UUID alertId;
    private String eventType;
    private List<ChannelResult> channelResults;
    private OffsetDateTime processedAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChannelResult {
        private String channel;
        private boolean sent;
        private String detail;
    }
}
