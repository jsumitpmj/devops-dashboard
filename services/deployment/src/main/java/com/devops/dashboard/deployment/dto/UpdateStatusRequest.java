package com.devops.dashboard.deployment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private String status;      // pending, in_progress, succeeded, failed, rolled_back, cancelled

    private String errorMessage; // only for failed status
}
