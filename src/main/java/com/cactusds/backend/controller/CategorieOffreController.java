package com.cactusds.backend.controller;

import com.cactusds.backend.dto.CategorieOffreRequest;
import com.cactusds.backend.dto.CategorieOffreResponse;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.repository.CategorieOffreRepository;
import com.cactusds.backend.repository.OffreRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategorieOffreController {

    private final CategorieOffreRepository categorieOffreRepository;
    private final OffreRepository offreRepository;

    public CategorieOffreController(CategorieOffreRepository categorieOffreRepository, OffreRepository offreRepository) {
        this.categorieOffreRepository = categorieOffreRepository;
        this.offreRepository = offreRepository;
    }

    @GetMapping("/categories")
    public List<CategorieOffreResponse> listActive() {
        return categorieOffreRepository.findByActifTrueOrderByOrdreAffichageAsc()
                .stream().map(CategorieOffreResponse::from).toList();
    }

    @GetMapping("/admin/categories")
    public List<CategorieOffreResponse> listAllAdmin() {
        return categorieOffreRepository.findAll().stream().map(CategorieOffreResponse::from).toList();
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<CategorieOffreResponse> create(@Valid @RequestBody CategorieOffreRequest req) {
        if (categorieOffreRepository.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug already in use");
        }
        CategorieOffre categorie = CategorieOffre.builder()
                .nom(req.nom())
                .slug(req.slug())
                .description(req.description())
                .icone(req.icone())
                .famille(req.famille() != null ? req.famille() : CategorieOffre.Famille.SITE_WEB)
                .ordreAffichage(req.ordreAffichage() != null ? req.ordreAffichage() : 0)
                .actif(req.actif() != null ? req.actif() : true)
                .build();
        categorieOffreRepository.save(categorie);
        return ResponseEntity.status(201).body(CategorieOffreResponse.from(categorie));
    }

    @PutMapping("/admin/categories/{id}")
    public ResponseEntity<CategorieOffreResponse> update(@PathVariable Long id, @Valid @RequestBody CategorieOffreRequest req) {
        return categorieOffreRepository.findById(id)
                .map(categorie -> {
                    if (categorieOffreRepository.existsBySlugAndIdNot(req.slug(), id)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug already in use");
                    }
                    categorie.setNom(req.nom());
                    categorie.setSlug(req.slug());
                    categorie.setDescription(req.description());
                    categorie.setIcone(req.icone());
                    if (req.famille() != null) categorie.setFamille(req.famille());
                    if (req.ordreAffichage() != null) categorie.setOrdreAffichage(req.ordreAffichage());
                    if (req.actif() != null) categorie.setActif(req.actif());
                    categorieOffreRepository.save(categorie);
                    return ResponseEntity.ok(CategorieOffreResponse.from(categorie));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!categorieOffreRepository.existsById(id)) return ResponseEntity.notFound().build();
        if (offreRepository.existsByCategorieId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete a category that still has offers");
        }
        categorieOffreRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}