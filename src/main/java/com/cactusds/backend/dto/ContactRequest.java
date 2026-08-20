package com.cactusds.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ContactRequest(
        @NotBlank String nom,
        @NotBlank @Email String email,
        String telephone,
        String sujet,
        @NotBlank String message
) {}
