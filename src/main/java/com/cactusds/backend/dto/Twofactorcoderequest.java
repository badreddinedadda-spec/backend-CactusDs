package com.cactusds.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record Twofactorcoderequest(@NotBlank String code) {}