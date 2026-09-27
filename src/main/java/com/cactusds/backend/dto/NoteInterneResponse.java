package com.cactusds.backend.dto;

import com.cactusds.backend.model.NoteInterne;
import java.time.LocalDateTime;

public record NoteInterneResponse(Long id, String contenu, String auteurNom, LocalDateTime createdAt) {
    public static NoteInterneResponse from(NoteInterne n) {
        return new NoteInterneResponse(n.getId(), n.getContenu(), n.getAuteur().getFullName(), n.getCreatedAt());
    }
}