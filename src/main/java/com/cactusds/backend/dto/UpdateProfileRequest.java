package com.cactusds.backend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record UpdateProfileRequest(@NotBlank @Size(max = 150) String fullName) {
}
