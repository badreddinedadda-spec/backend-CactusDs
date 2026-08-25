package com.cactusds.backend.repository;
import com.cactusds.backend.model.Facture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FactureRepository extends JpaRepository<Facture, Long> {
    List<Facture> findByUserIdOrderByDateEmissionDesc(Long userId);
    long countByNumeroStartingWith(String prefix);
}
