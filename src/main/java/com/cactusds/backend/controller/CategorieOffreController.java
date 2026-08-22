package com.cactusds.backend.controller;

import com.cactusds.backend.dto.CategorieOffreResponse;
import com.cactusds.backend.repository.CategorieOffreRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategorieOffreController {

    private final CategorieOffreRepository categorieOffreRepository;

    public CategorieOffreController(CategorieOffreRepository categorieOffreRepository) {
        this.categorieOffreRepository = categorieOffreRepository;
    }

    @GetMapping
    public List<CategorieOffreResponse> listActive() {
        return categorieOffreRepository.findByActifTrueOrderByOrdreAffichageAsc()
                .stream().map(CategorieOffreResponse::from).toList();
    }
}