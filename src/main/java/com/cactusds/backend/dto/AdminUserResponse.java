package com.cactusds.backend.dto;
import com.cactusds.backend.model.User;
import java.time.LocalDateTime;

public record AdminUserResponse(Long id, String email, String fullName, String role, String authProvider, LocalDateTime createdAt) {
    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.getAuthProvider().name(), u.getCreatedAt());
    }
}