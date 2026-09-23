package com.cactusds.backend.dto;

import java.time.LocalDateTime;

public record SessionResponse(
        Long id,
        String device,
        String ipAddress,
        LocalDateTime createdAt,
        LocalDateTime lastSeenAt,
        boolean current
) {}