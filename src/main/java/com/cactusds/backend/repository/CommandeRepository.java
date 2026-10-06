package com.cactusds.backend.repository;

import com.cactusds.backend.model.Commande;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
public interface CommandeRepository extends JpaRepository<Commande, Long>{

    @EntityGraph(attributePaths = {"offre", "offre.categorie"})
    List<Commande> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Commande> findByUserIdAndFactureIsNullAndDateDebutBetween(Long userId, LocalDate start, LocalDate end);
    List<Commande> findByFactureIdOrderByCreatedAtAsc(Long factureId);
    List<Commande> findByDateExpirationAndReminderSentFalse(LocalDate date, Commande.Statut active);
    List<Commande> findByFactureIsNullOrderByUserIdAscDateDebutAsc();

    @Query("select c from Commande c join fetch c.offre o join fetch o.categorie where c.id = :id and c.user.id = :userId")
    Optional<Commande> findOwnedWithOffre(@Param("id") Long id, @Param("userId") Long userId);

    @Override
    @EntityGraph(attributePaths = {"offre", "offre.categorie"})
    List<Commande> findAll();
}

