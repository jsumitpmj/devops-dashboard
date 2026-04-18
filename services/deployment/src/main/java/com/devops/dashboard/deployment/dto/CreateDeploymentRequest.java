package com.devops.dashboard.deployment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateDeploymentRequest {

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @NotNull(message = "Environment ID is required")
    private UUID environmentId;

    private String gitRef;
    private String gitSha;
    private String imageTag;
}
