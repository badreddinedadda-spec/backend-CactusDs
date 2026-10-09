package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.dto.*;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.TicketMessage;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.TicketRepository;
import com.cactusds.backend.comon.ticket.TicketThreadService;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class TicketController {

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final CurrentUserResolver currentUserResolver;
    private final TicketThreadService threadService;
    public TicketController(TicketRepository ticketRepository, NotificationService notificationService, CurrentUserResolver currentUserResolver
    , TicketThreadService threadService) {
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
        this.threadService = threadService;
    }

    @PostMapping("/api/client/tickets")
    public ResponseEntity<TicketResponse> create(
            @Valid @RequestBody TicketRequest req,
            Authentication authentication) {

        User user = currentUserResolver.resolve(authentication);

        Ticket ticket = Ticket.builder()
                .user(user)
                .categorie(req.categorie() != null
                        ? req.categorie()
                        : Ticket.Categorie.GENERAL)
                .sujet(req.sujet())
                .message(req.message())
                .priorite(req.priorite() != null
                        ? req.priorite()
                        : Ticket.Priorite.NORMALE)
                .statut(Ticket.Statut.OUVERT)
                .build();

        Ticket saved = ticketRepository.save(ticket);

        notificationService.notifyTicketClientReply(
                saved, saved.getMessage());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TicketResponse.from(saved));
    }

    @GetMapping("/api/client/tickets")
    public List<TicketResponse> myTickets(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(TicketResponse::from).toList();
    }

    @GetMapping("/api/admin/tickets")
    public List<TicketResponse> allTickets(@RequestParam(required = false) Ticket.Categorie categorie) {
        return ticketRepository.findAll().stream()
                .filter(t -> categorie == null || t.getCategorie() == categorie)
                .map(TicketResponse::from)
                .toList();
    }

    @PutMapping("/api/admin/tickets/{id}/reply")
    public ResponseEntity<TicketResponse> reply(@PathVariable Long id, @Valid @RequestBody TicketReplyRequest req) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    threadService.appendSupportMessage(ticket, req.reponseAdmin());
                    ticket.setReponseAdmin(req.reponseAdmin());
                    ticket.setStatut(req.statut() != null ? req.statut() : Ticket.Statut.RESOLU);
                    if (req.priorite() != null) {
                        ticket.setPriorite(req.priorite());
                    }
                    ticket.setUpdatedAt(LocalDateTime.now());
                    ticketRepository.save(ticket);
                    notificationService.notifyTicketReply(ticket);
                    return ResponseEntity.ok(TicketResponse.from(ticket));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/api/admin/tickets/{id}/priorite")
    public ResponseEntity<TicketResponse> updatePriorite(@PathVariable Long id, @Valid @RequestBody TicketPrioriteUpdateRequest req) {
        return ticketRepository.findById(id)
                .map(ticket -> {
                    ticket.setPriorite(req.priorite());
                    ticket.setUpdatedAt(LocalDateTime.now());
                    ticketRepository.save(ticket);
                    return ResponseEntity.ok(TicketResponse.from(ticket));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}