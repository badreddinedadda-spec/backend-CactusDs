package com.cactusds.backend.controller;

import com.cactusds.backend.dto.*;
import com.cactusds.backend.model.Domaine;
import com.cactusds.backend.model.SslCertificat;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.DomaineRepository;
import com.cactusds.backend.repository.SslCertificatRepository;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class DomaineController {

    private final DomaineRepository domaineRepository;
    private final SslCertificatRepository sslCertificatRepository;
    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;

    public DomaineController(DomaineRepository domaineRepository, SslCertificatRepository sslCertificatRepository,
                             UserRepository userRepository, CurrentUserResolver currentUserResolver) {
        this.domaineRepository = domaineRepository;
        this.sslCertificatRepository = sslCertificatRepository;
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/api/client/domaines")
    public List<DomaineResponse> myDomaines(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return domaineRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(DomaineResponse::from).toList();
    }

    @GetMapping("/api/admin/domaines")
    public List<DomaineResponse> allDomaines() {
        return domaineRepository.findAll().stream().map(DomaineResponse::from).toList();
    }

    @PostMapping("/api/admin/domaines")
    public ResponseEntity<DomaineResponse> create(@Valid @RequestBody DomaineRequest req) {
        if (req.userId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required");
        }
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown userId"));

        Domaine domaine = Domaine.builder()
                .user(user)
                .nom(req.nom())
                .extension(req.extension())
                .dateExpiration(req.dateExpiration())
                .statut(req.statut() != null ? req.statut() : Domaine.Statut.ACTIF)
                .renouvellementAuto(req.renouvellementAuto() != null ? req.renouvellementAuto() : true)
                .build();

        domaineRepository.save(domaine);
        return ResponseEntity.status(201).body(DomaineResponse.from(domaine));
    }

    @PutMapping("/api/admin/domaines/{id}")
    public ResponseEntity<DomaineResponse> update(@PathVariable Long id, @Valid @RequestBody DomaineRequest req) {
        return domaineRepository.findById(id)
                .map(domaine -> {
                    domaine.setNom(req.nom());
                    domaine.setExtension(req.extension());
                    domaine.setDateExpiration(req.dateExpiration());
                    if (req.statut() != null) domaine.setStatut(req.statut());
                    if (req.renouvellementAuto() != null) domaine.setRenouvellementAuto(req.renouvellementAuto());
                    domaineRepository.save(domaine);
                    return ResponseEntity.ok(DomaineResponse.from(domaine));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/admin/domaines/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!domaineRepository.existsById(id)) return ResponseEntity.notFound().build();
        domaineRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/domaines/{domaineId}/ssl")
    public ResponseEntity<SslCertificatResponse> createSsl(@PathVariable Long domaineId,
                                                           @Valid @RequestBody SslCertificatRequest req) {
        Domaine domaine = domaineRepository.findById(domaineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown domaineId"));

        if (sslCertificatRepository.findByDomaineId(domaineId).isPresent()) {
            return ResponseEntity.status(409).build();
        }

        SslCertificat ssl = SslCertificat.builder()
                .domaine(domaine)
                .type(req.type() != null ? req.type() : SslCertificat.Type.GRATUIT)
                .dateExpiration(req.dateExpiration())
                .statut(req.statut() != null ? req.statut() : SslCertificat.Statut.ACTIF)
                .build();

        sslCertificatRepository.save(ssl);
        return ResponseEntity.status(201).body(SslCertificatResponse.from(ssl));
    }

    @PutMapping("/api/admin/domaines/{domaineId}/ssl")
    public ResponseEntity<SslCertificatResponse> updateSsl(@PathVariable Long domaineId,
                                                           @Valid @RequestBody SslCertificatRequest req) {
        return sslCertificatRepository.findByDomaineId(domaineId)
                .map(ssl -> {
                    if (req.type() != null) ssl.setType(req.type());
                    ssl.setDateExpiration(req.dateExpiration());
                    if (req.statut() != null) ssl.setStatut(req.statut());
                    sslCertificatRepository.save(ssl);
                    return ResponseEntity.ok(SslCertificatResponse.from(ssl));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}