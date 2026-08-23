package com.cactusds.backend.controller;

import com.cactusds.backend.dto.PortfolioRequest;
import com.cactusds.backend.dto.PortfolioResponse;
import com.cactusds.backend.model.Portfolio;
import com.cactusds.backend.repository.PortfolioRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PortfolioController {

    private final PortfolioRepository portfolioRepository;

    public PortfolioController(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @GetMapping("/api/portfolio")
    public List<PortfolioResponse> listActive() {
        return portfolioRepository.findByActifTrueOrderByOrdreAsc()
                .stream().map(PortfolioResponse::from).toList();
    }

    @GetMapping("/api/admin/portfolio")
    public List<PortfolioResponse> listAllAdmin() {
        return portfolioRepository.findAll().stream().map(PortfolioResponse::from).toList();
    }

    @PostMapping("/api/admin/portfolio")
    public ResponseEntity<PortfolioResponse> create(@Valid @RequestBody PortfolioRequest req) {
        Portfolio portfolio = Portfolio.builder()
                .titre(req.titre())
                .description(req.description())
                .imageUrl(req.imageUrl())
                .lienUrl(req.lienUrl())
                .categorie(req.categorie())
                .client(req.client())
                .dateProjet(req.dateProjet())
                .enVedette(req.enVedette() != null ? req.enVedette() : false)
                .ordre(req.ordre() != null ? req.ordre() : 0)
                .actif(req.actif() != null ? req.actif() : true)
                .build();
        portfolioRepository.save(portfolio);
        return ResponseEntity.status(201).body(PortfolioResponse.from(portfolio));
    }

    @PutMapping("/api/admin/portfolio/{id}")
    public ResponseEntity<PortfolioResponse> update(@PathVariable Long id, @Valid @RequestBody PortfolioRequest req) {
        return portfolioRepository.findById(id)
                .map(portfolio -> {
                    portfolio.setTitre(req.titre());
                    portfolio.setDescription(req.description());
                    portfolio.setImageUrl(req.imageUrl());
                    portfolio.setLienUrl(req.lienUrl());
                    portfolio.setCategorie(req.categorie());
                    portfolio.setClient(req.client());
                    portfolio.setDateProjet(req.dateProjet());
                    if (req.enVedette() != null) portfolio.setEnVedette(req.enVedette());
                    if (req.ordre() != null) portfolio.setOrdre(req.ordre());
                    if (req.actif() != null) portfolio.setActif(req.actif());
                    portfolioRepository.save(portfolio);
                    return ResponseEntity.ok(PortfolioResponse.from(portfolio));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/admin/portfolio/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!portfolioRepository.existsById(id)) return ResponseEntity.notFound().build();
        portfolioRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}