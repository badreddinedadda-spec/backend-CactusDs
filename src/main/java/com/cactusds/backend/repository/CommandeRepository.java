package com.cactusds.backend.repository;

import com.cactusds.backend.model.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CommandeRepository extends JpaRepository<Commande, Long>{
    List<Commande> findByUserIdOrderByCreatedAtDesc(Long userId);
}
