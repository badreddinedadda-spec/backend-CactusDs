package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.dto.TicketReplyRequest;
import com.cactusds.backend.dto.TicketPrioriteUpdateRequest;
import com.cactusds.backend.dto.TicketRequest;
import com.cactusds.backend.dto.TicketResponse;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.TicketRepository;
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

    public TicketController(TicketRepository ticketRepository, NotificationService notificationService, CurrentUserResolver currentUserResolver) {
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/api/client/tickets")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest req, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Ticket ticket = Ticket.builder()
                .user(user)
                .categorie(req.categorie() != null ? req.categorie() : Ticket.Categorie.GENERAL)
                .sujet(req.sujet())
                .message(req.message())
                .priorite(req.priorite() != null ? req.priorite() : Ticket.Priorite.NORMALE)
                .build();
        ticketRepository.save(ticket);
        return ResponseEntity.status(201).body(TicketResponse.from(ticket));
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