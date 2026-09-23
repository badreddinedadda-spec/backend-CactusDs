package com.cactusds.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorCodeRequest(@NotBlank String code) {}