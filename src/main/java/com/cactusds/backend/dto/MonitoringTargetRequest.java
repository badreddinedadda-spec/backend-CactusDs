package com.cactusds.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MonitoringTargetRequest(
        @NotBlank @Size(max = 500) String agentUrl, Boolean enabled) {
}