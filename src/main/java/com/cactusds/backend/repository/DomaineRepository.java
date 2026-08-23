package com.cactusds.backend.repository;

import com.cactusds.backend.model.Domaine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DomaineRepository extends JpaRepository<Domaine, Long> {
    List<Domaine> findByUserIdOrderByCreatedAtDesc(Long userId);
}