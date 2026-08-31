package com.cactusds.backend.repository;

import com.cactusds.backend.model.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDate;
public interface CommandeRepository extends JpaRepository<Commande, Long>{
    List<Commande> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Commande> findByUserIdAndFactureIsNullAndDateDebutBetween(Long userId, LocalDate start, LocalDate end);
    List<Commande> findByFactureIdOrderByCreatedAtAsc(Long factureId);
    List<Commande> findByDateExpirationAndReminderSentFalse(LocalDate date);
}
