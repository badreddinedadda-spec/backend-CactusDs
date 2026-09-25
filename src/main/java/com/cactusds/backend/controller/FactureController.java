package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.comon.pdf.FacturePdfGenerator;
import com.cactusds.backend.dto.BankTransferInfoResponse;
import com.cactusds.backend.dto.FactureGenerateRequest;
import com.cactusds.backend.dto.FactureResponse;
import com.cactusds.backend.dto.FactureStatutUpdateRequest;
import com.cactusds.backend.dto.PendingInvoiceGroupResponse;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.FactureRepository;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class FactureController {
    private final FactureRepository factureRepository;
    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final FacturePdfGenerator pdfGenerator;
    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;

    @Value("${app.payment.bank.name:}")
    private String bankName;
    @Value("${app.payment.bank.rib:}")
    private String bankRib;
    @Value("${app.payment.bank.iban:}")
    private String bankIban;
    @Value("${app.payment.bank.holder:}")
    private String bankHolder;

    public FactureController(FactureRepository factureRepository, CommandeRepository commandeRepository,
                             UserRepository userRepository, FacturePdfGenerator pdfGenerator,
                             NotificationService notificationService, CurrentUserResolver currentUserResolver) {
        this.factureRepository = factureRepository;
        this.commandeRepository = commandeRepository;
        this.userRepository = userRepository;
        this.pdfGenerator = pdfGenerator;
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/api/admin/factures/generate")
    public ResponseEntity<FactureResponse> generate(@Valid @RequestBody FactureGenerateRequest req) {
        if (req.periodeDebut().isAfter(req.periodeFin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "periodeDebut must be before periodeFin");
        }
        User client = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown userId"));

        List<Commande> commandes = commandeRepository
                .findByUserIdAndFactureIsNullAndDateDebutBetween(client.getId(), req.periodeDebut(), req.periodeFin());
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
                .periodeDebut(req.periodeDebut())
                .periodeFin(req.periodeFin())
                .montantTotal(total)
                .build();
        factureRepository.save(facture);

        commandes.forEach(c -> c.setFacture(facture));
        commandeRepository.saveAll(commandes);
        notificationService.notifyFactureGenerated(facture);

        return ResponseEntity.status(201).body(FactureResponse.from(facture, commandes));
    }

    @GetMapping("/api/admin/factures/pending")
    public List<PendingInvoiceGroupResponse> pendingInvoices() {
        List<Commande> uninvoiced = commandeRepository.findByFactureIsNullOrderByUserIdAscDateDebutAsc();
        Map<Long, List<Commande>> byUser = uninvoiced.stream()
                .collect(Collectors.groupingBy(c -> c.getUser().getId(), LinkedHashMap::new, Collectors.toList()));

        return byUser.values().stream()
                .map(list -> {
                    User client = list.get(0).getUser();
                    BigDecimal total = list.stream().map(Commande::getPrixTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    LocalDate min = list.stream().map(Commande::getDateDebut).min(LocalDate::compareTo).orElse(null);
                    LocalDate max = list.stream().map(Commande::getDateDebut).max(LocalDate::compareTo).orElse(null);
                    return new PendingInvoiceGroupResponse(client.getId(), client.getEmail(), client.getFullName(),
                            list.size(), total, min, max);
                })
                .sorted(Comparator.comparing(PendingInvoiceGroupResponse::montantTotal).reversed())
                .toList();
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
                    Facture.Statut previousStatut = facture.getStatut();
                    facture.setStatut(req.statut());
                    factureRepository.save(facture);

                    List<Commande> commandes = commandeRepository.findByFactureIdOrderByCreatedAtAsc(id);
                    if (req.statut() == Facture.Statut.PAYEE && previousStatut != Facture.Statut.PAYEE) {
                        commandes.stream()
                                .filter(c -> c.getStatut() == Commande.Statut.EN_ATTENTE)
                                .forEach(c -> c.setStatut(Commande.Statut.ACTIVE));
                        commandeRepository.saveAll(commandes);
                    } else if (previousStatut == Facture.Statut.PAYEE && req.statut() != Facture.Statut.PAYEE) {
                        commandes.stream()
                                .filter(c -> c.getStatut() == Commande.Statut.ACTIVE)
                                .forEach(c -> c.setStatut(Commande.Statut.EN_ATTENTE));
                        commandeRepository.saveAll(commandes);
                    }

                    return ResponseEntity.ok(FactureResponse.from(facture, commandes));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Manual "send a reminder now" action for the admin Relances screen. The daily
     * BillingAutomationJob already reminds an unpaid invoice once automatically after 7 days
     * (and suspends its active commandes after 15), but an admin may want to nudge a client
     * sooner, e.g. right after a phone call. Unlike the automatic job, this can be called more
     * than once: relanceEnvoyee is still set to true so the automatic job does not also send its
     * own reminder the same week, but a repeated manual click here is a deliberate admin action,
     * not a bug to guard against.
     */
    @PostMapping("/api/admin/factures/{id}/relancer")
    public ResponseEntity<FactureResponse> sendReminder(@PathVariable Long id) {
        Facture facture = factureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (facture.getStatut() != Facture.Statut.EMISE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only an unpaid (EMISE) invoice can be reminded");
        }
        notificationService.notifyFactureOverdueReminder(facture);
        facture.setRelanceEnvoyee(true);
        factureRepository.save(facture);

        List<Commande> commandes = commandeRepository.findByFactureIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(FactureResponse.from(facture, commandes));
    }

    @GetMapping("/api/admin/factures/{id}/pdf")
    public ResponseEntity<byte[]> adminPdf(@PathVariable Long id) {
        Facture facture = factureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return pdfResponse(facture);
    }

    @GetMapping("/api/client/factures")
    public List<FactureResponse> myFactures(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return factureRepository.findByUserIdOrderByDateEmissionDesc(user.getId()).stream()
                .map(f -> FactureResponse.from(f, commandeRepository.findByFactureIdOrderByCreatedAtAsc(f.getId())))
                .toList();
    }

    /** Bank details for the client "Payer" screen. {@code configured=false} until an admin fills
     * in the BANK_* environment variables — the frontend then shows a "contact support" message
     * instead of blank or fabricated numbers. */
    @GetMapping("/api/client/paiement/virement")
    public BankTransferInfoResponse bankTransferInfo() {
        boolean configured = bankRib != null && !bankRib.isBlank();
        return new BankTransferInfoResponse(configured, bankName, bankRib, bankIban, bankHolder);
    }

    /**
     * The client clicks "J'ai effectué le virement" — this only RECORDS that claim and alerts the
     * admin by email; it never changes {@code statut} itself. Only an admin confirming against the
     * real bank statement (PUT /api/admin/factures/{id}/statut) marks an invoice PAYEE. Calling
     * this a second time for the same invoice is a harmless no-op: the timestamp and the admin
     * email are only ever set/sent once.
     */
    @PostMapping("/api/client/factures/{id}/declarer-paiement")
    public ResponseEntity<FactureResponse> declarePaiement(@PathVariable Long id, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Facture facture = factureRepository.findById(id)
                .filter(f -> f.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (facture.getStatut() != Facture.Statut.EMISE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This invoice is not awaiting payment");
        }
        if (facture.getPaiementDeclareAt() == null) {
            facture.setPaiementDeclareAt(LocalDateTime.now());
            factureRepository.save(facture);
            notificationService.notifyPaiementDeclare(facture);
        }
        List<Commande> commandes = commandeRepository.findByFactureIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(FactureResponse.from(facture, commandes));
    }

    @GetMapping("/api/client/factures/{id}/pdf")
    public ResponseEntity<byte[]> myFacturePdf(@PathVariable Long id, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
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

    private String nextNumero() {
        int year = LocalDate.now().getYear();
        String prefix = "FAC-" + year + "-";
        long count = factureRepository.countByNumeroStartingWith(prefix);
        return prefix + String.format("%04d", count + 1);
    }
}