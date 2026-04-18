package com.devops.dashboard.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthResponse {

    private String status;      // "ok", "degraded", "down"
    private String service;
    private String version;
    private long uptime;        // seconds
    private String timestamp;
    private DatabaseHealth database;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DatabaseHealth {
        private boolean ok;
        private long latencyMs;
    }
}
