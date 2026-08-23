package com.cactusds.backend.controller;
import com.cactusds.backend.dto.CommandeRequest;
import com.cactusds.backend.dto.CommandeResponse;
import com.cactusds.backend.dto.StatutUpdateRequest;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Offre;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.OffreRepository;
import com.cactusds.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
@RestController
public class CommandeController {

    private final CommandeRepository commandeRepository;
    private final OffreRepository offreRepository;
    private final UserRepository userRepository;

    public CommandeController(CommandeRepository commandeRepository, OffreRepository offreRepository,
                              UserRepository userRepository) {
        this.commandeRepository = commandeRepository;
        this.offreRepository = offreRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/api/client/commandes")
    public ResponseEntity<CommandeResponse> create(@Valid @RequestBody CommandeRequest req,
                                                   Authentication authentication) {
        User user = currentUser(authentication);

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
        User user = currentUser(authentication);
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

    private User currentUser(Authentication authentication) {
        String email = extractEmail(authentication);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
}