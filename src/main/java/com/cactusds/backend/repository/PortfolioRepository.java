package com.cactusds.backend.repository;

import com.cactusds.backend.model.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByActifTrueOrderByOrdreAsc();
}