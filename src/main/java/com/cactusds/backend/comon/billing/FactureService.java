package com.cactusds.backend.comon.billing;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.FactureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FactureService {

    private static final Logger log = LoggerFactory.getLogger(FactureService.class);

    private final FactureRepository factureRepository;
    private final CommandeRepository commandeRepository;
    private final NotificationService notificationService;

    public FactureService(FactureRepository factureRepository, CommandeRepository commandeRepository,
                          NotificationService notificationService) {
        this.factureRepository = factureRepository;
        this.commandeRepository = commandeRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Facture generateForPeriod(User client, LocalDate periodeDebut, LocalDate periodeFin) {
        if (periodeDebut.isAfter(periodeFin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodeDebut must be before periodeFin");
        }

        List<Commande> commandes = commandeRepository
                .findByUserIdAndFactureIsNullAndDateDebutBetween(client.getId(), periodeDebut, periodeFin);
        if (commandes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No uninvoiced commandes found for this client in the given period");
        }

        BigDecimal total = commandes.stream()
                .map(Commande::getPrixTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Facture facture = Facture.builder()
                .numero(nextNumero())
                .user(client)
                .periodeDebut(periodeDebut)
                .periodeFin(periodeFin)
                .montantTotal(total)
                .build();
        factureRepository.save(facture);

        commandes.forEach(c -> c.setFacture(facture));
        commandeRepository.saveAll(commandes);
        notificationService.notifyFactureGenerated(facture);

        return facture;
    }

    @Transactional
    public void generateForNewCommande(Commande commande) {
        try {
            generateForPeriod(commande.getUser(), commande.getDateDebut(), commande.getDateExpiration());
        } catch (ResponseStatusException e) {
            log.warn("Auto-invoicing skipped for commande {}: {}", commande.getId(), e.getReason());
        }
    }

    private String nextNumero() {
        int year = LocalDate.now().getYear();
        String prefix = "FAC-" + year + "-";
        long count = factureRepository.countByNumeroStartingWith(prefix);
        return prefix + String.format("%04d", count + 1);
    }
}