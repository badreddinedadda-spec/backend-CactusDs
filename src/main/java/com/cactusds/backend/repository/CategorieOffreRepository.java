package com.cactusds.backend.repository;

import com.cactusds.backend.model.CategorieOffre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategorieOffreRepository extends JpaRepository<CategorieOffre, Long> {
    List<CategorieOffre> findByActifTrueOrderByOrdreAffichageAsc();
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
}
