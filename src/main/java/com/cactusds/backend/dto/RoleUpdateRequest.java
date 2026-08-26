package com.cactusds.backend.dto;
import com.cactusds.backend.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(@NotNull Role role) {}