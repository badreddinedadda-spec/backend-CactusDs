package com.cactusds.backend.controller;
import com.cactusds.backend.dto.CommandeRequest;
import com.cactusds.backend.dto.CommandeResponse;
import com.cactusds.backend.dto.StatutUpdateRequest;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Offre;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.OffreRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@RestController
public class CommandeController {

    private final CommandeRepository commandeRepository;
    private final OffreRepository offreRepository;
    private final CurrentUserResolver currentUserResolver;

    public CommandeController(CommandeRepository commandeRepository, OffreRepository offreRepository,
                              CurrentUserResolver currentUserResolver) {
        this.commandeRepository = commandeRepository;
        this.offreRepository = offreRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/api/client/commandes")
    public ResponseEntity<CommandeResponse> create(@Valid @RequestBody CommandeRequest req,
                                                   Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        Offre offre = offreRepository.findById(req.offreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown offreId"));

        BigDecimal prixTotal = req.duree() == Commande.Duree.ANNUEL ? offre.getPrixAnnuel() : offre.getPrixMensuel();
        if (prixTotal == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This offer has no annual price set");
        }

        LocalDate dateDebut = LocalDate.now();
        LocalDate dateExpiration = req.duree() == Commande.Duree.ANNUEL
                ? dateDebut.plusYears(1)
                : dateDebut.plusMonths(1);

        Commande commande = Commande.builder()
                .user(user)
                .offre(offre)
                .duree(req.duree())
                .prixTotal(prixTotal)
                .dateDebut(dateDebut)
                .dateExpiration(dateExpiration)
                .build();

        commandeRepository.save(commande);
        return ResponseEntity.status(201).body(CommandeResponse.from(commande));
    }

    @GetMapping("/api/client/commandes")
    public List<CommandeResponse> myCommandes(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return commandeRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(CommandeResponse::from).toList();
    }

    @GetMapping("/api/admin/commandes")
    public List<CommandeResponse> allCommandes() {
        return commandeRepository.findAll().stream().map(CommandeResponse::from).toList();
    }

    @PutMapping("/api/admin/commandes/{id}/statut")
    public ResponseEntity<CommandeResponse> updateStatut(@PathVariable Long id,
                                                         @Valid @RequestBody StatutUpdateRequest req) {
        return commandeRepository.findById(id)
                .map(commande -> {
                    commande.setStatut(req.statut());
                    commandeRepository.save(commande);
                    return ResponseEntity.ok(CommandeResponse.from(commande));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}