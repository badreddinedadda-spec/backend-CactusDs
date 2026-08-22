package com.cactusds.backend.controller;

import com.cactusds.backend.dto.OffreRequest;
import com.cactusds.backend.dto.OffreResponse;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Offre;
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
public class OffreController {

    private final OffreRepository offreRepository;
    private final CategorieOffreRepository categorieOffreRepository;

    public OffreController(OffreRepository offreRepository, CategorieOffreRepository categorieOffreRepository) {
        this.offreRepository = offreRepository;
        this.categorieOffreRepository = categorieOffreRepository;
    }

    // Public catalog — ?categorieId=X filters, omit it for everything active
    @GetMapping("/offres")
    public List<OffreResponse> listActive(@RequestParam(required = false) Long categorieId) {
        List<Offre> offres = categorieId != null
                ? offreRepository.findByCategorieIdAndActifTrueOrderByOrdreAffichageAsc(categorieId)
                : offreRepository.findByActifTrueOrderByOrdreAffichageAsc();
        return offres.stream().map(OffreResponse::from).toList();
    }

    @GetMapping("/offres/{id}")
    public ResponseEntity<OffreResponse> getOne(@PathVariable Long id) {
        return offreRepository.findById(id)
                .filter(Offre::getActif)
                .map(o -> ResponseEntity.ok(OffreResponse.from(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Admin — sees inactive offers too, since managing them is the point
    @GetMapping("/admin/offres")
    public List<OffreResponse> listAllAdmin() {
        return offreRepository.findAll().stream().map(OffreResponse::from).toList();
    }

    @PostMapping("/admin/offres")
    public ResponseEntity<OffreResponse> create(@Valid @RequestBody OffreRequest req) {
        CategorieOffre categorie = categorieOffreRepository.findById(req.categorieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown categorieId"));

        Offre offre = Offre.builder()
                .categorie(categorie)
                .nom(req.nom())
                .description(req.description())
                .prixMensuel(req.prixMensuel())
                .prixAnnuel(req.prixAnnuel())
                .espaceDisqueGo(req.espaceDisqueGo())
                .bandePassanteGo(req.bandePassanteGo())
                .nbDomaines(req.nbDomaines() != null ? req.nbDomaines() : 1)
                .nbEmails(req.nbEmails() != null ? req.nbEmails() : 5)
                .sslInclus(req.sslInclus() != null ? req.sslInclus() : false)
                .actif(req.actif() != null ? req.actif() : true)
                .ordreAffichage(req.ordreAffichage() != null ? req.ordreAffichage() : 0)
                .build();

        offreRepository.save(offre);
        return ResponseEntity.status(201).body(OffreResponse.from(offre));
    }

    @PutMapping("/admin/offres/{id}")
    public ResponseEntity<OffreResponse> update(@PathVariable Long id, @Valid @RequestBody OffreRequest req) {
        return offreRepository.findById(id)
                .map(offre -> {
                    CategorieOffre categorie = categorieOffreRepository.findById(req.categorieId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown categorieId"));
                    offre.setCategorie(categorie);
                    offre.setNom(req.nom());
                    offre.setDescription(req.description());
                    offre.setPrixMensuel(req.prixMensuel());
                    offre.setPrixAnnuel(req.prixAnnuel());
                    offre.setEspaceDisqueGo(req.espaceDisqueGo());
                    offre.setBandePassanteGo(req.bandePassanteGo());
                    if (req.nbDomaines() != null) offre.setNbDomaines(req.nbDomaines());
                    if (req.nbEmails() != null) offre.setNbEmails(req.nbEmails());
                    if (req.sslInclus() != null) offre.setSslInclus(req.sslInclus());
                    if (req.actif() != null) offre.setActif(req.actif());
                    if (req.ordreAffichage() != null) offre.setOrdreAffichage(req.ordreAffichage());
                    offreRepository.save(offre);
                    return ResponseEntity.ok(OffreResponse.from(offre));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/offres/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!offreRepository.existsById(id)) return ResponseEntity.notFound().build();
        offreRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}