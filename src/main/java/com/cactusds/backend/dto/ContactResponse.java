package com.cactusds.backend.dto;

import com.cactusds.backend.model.Contact;
import java.time.LocalDateTime;

public record ContactResponse(
        Long id, String nom, String email, String telephone,
        String sujet, String message, Boolean lu, LocalDateTime createdAt
) {
    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.getId(), contact.getNom(), contact.getEmail(), contact.getTelephone(),
                contact.getSujet(), contact.getMessage(), contact.getLu(), contact.getCreatedAt()
        );
    }
}