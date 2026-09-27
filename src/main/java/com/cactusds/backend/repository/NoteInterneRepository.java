package com.cactusds.backend.repository;

import com.cactusds.backend.model.NoteInterne;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoteInterneRepository extends JpaRepository<NoteInterne, Long> {
    List<NoteInterne> findByUser_IdOrderByCreatedAtDesc(Long userId);
}

