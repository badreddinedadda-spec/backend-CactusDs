package com.cactusds.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteInterneRequest(@NotBlank @Size(max = 2000) String contenu) {

}