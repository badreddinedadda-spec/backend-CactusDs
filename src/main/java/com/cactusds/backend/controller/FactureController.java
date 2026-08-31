package com.cactusds.backend.controller;

import com.cactusds.backend.comon.billing.FactureService;
import com.cactusds.backend.comon.pdf.FacturePdfGenerator;
import com.cactusds.backend.dto.FactureGenerateRequest;
import com.cactusds.backend.dto.FactureResponse;
import com.cactusds.backend.dto.FactureStatutUpdateRequest;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.FactureRepository;
import com.cactusds.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class FactureController {
    private final FactureRepository factureRepository;
    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final FacturePdfGenerator pdfGenerator;
    private final FactureService factureService;

    public FactureController(FactureRepository factureRepository, CommandeRepository commandeRepository,
                             UserRepository userRepository, FacturePdfGenerator pdfGenerator,
                             FactureService factureService) {
        this.factureRepository = factureRepository;
        this.commandeRepository = commandeRepository;
        this.userRepository = userRepository;
        this.pdfGenerator = pdfGenerator;
        this.factureService = factureService;
    }

    @PostMapping("/api/admin/factures/generate")
    public ResponseEntity<FactureResponse> generate(@Valid @RequestBody FactureGenerateRequest req) {
        User client = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown userId"));

        Facture facture = factureService.generateForPeriod(client, req.periodeDebut(), req.periodeFin());
        List<Commande> commandes = commandeRepository.findByFactureIdOrderByCreatedAtAsc(facture.getId());
        return ResponseEntity.status(201).body(FactureResponse.from(facture, commandes));
    }

    @GetMapping("/api/admin/factures")
    public List<FactureResponse> allFactures() {
        return factureRepository.findAll().stream()
                .map(f -> FactureResponse.from(f, commandeRepository.findByFactureIdOrderByCreatedAtAsc(f.getId())))
                .toList();
    }

    @GetMapping("/api/admin/factures/{id}")
    public FactureResponse getFacture(@PathVariable Long id) {
        Facture facture = factureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return FactureResponse.from(facture, commandeRepository.findByFactureIdOrderByCreatedAtAsc(id));
    }

    @PutMapping("/api/admin/factures/{id}/statut")
    public ResponseEntity<FactureResponse> updateStatut(@PathVariable Long id,
                                                        @Valid @RequestBody FactureStatutUpdateRequest req) {
        return factureRepository.findById(id)
                .map(facture -> {
                    facture.setStatut(req.statut());
                    factureRepository.save(facture);
                    return ResponseEntity.ok(FactureResponse.from(facture,
                            commandeRepository.findByFactureIdOrderByCreatedAtAsc(id)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/admin/factures/{id}/pdf")
    public ResponseEntity<byte[]> adminPdf(@PathVariable Long id) {
        Facture facture = factureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return pdfResponse(facture);
    }

    @GetMapping("/api/client/factures")
    public List<FactureResponse> myFactures(Authentication authentication) {
        User user = currentUser(authentication);
        return factureRepository.findByUserIdOrderByDateEmissionDesc(user.getId()).stream()
                .map(f -> FactureResponse.from(f, commandeRepository.findByFactureIdOrderByCreatedAtAsc(f.getId())))
                .toList();
    }

    @GetMapping("/api/client/factures/{id}/pdf")
    public ResponseEntity<byte[]> myFacturePdf(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        Facture facture = factureRepository.findById(id)
                .filter(f -> f.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return pdfResponse(facture);
    }

    private ResponseEntity<byte[]> pdfResponse(Facture facture) {
        List<Commande> commandes = commandeRepository.findByFactureIdOrderByCreatedAtAsc(facture.getId());
        byte[] pdf = pdfGenerator.generate(facture, commandes);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(facture.getNumero() + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
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