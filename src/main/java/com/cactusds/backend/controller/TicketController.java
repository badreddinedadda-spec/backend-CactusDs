package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.dto.TicketPrioriteUpdateRequest;
import com.cactusds.backend.dto.TicketReplyRequest;
import com.cactusds.backend.dto.TicketRequest;
import com.cactusds.backend.dto.TicketResponse;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.TicketRepository;
import com.cactusds.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class TicketController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public TicketController(TicketRepository ticketRepository, UserRepository userRepository, NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/api/client/tickets")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest req, Authentication authentication) {
        User user = currentUser(authentication);
        Ticket ticket = Ticket.builder()
                .user(user)
                .categorie(req.categorie() != null ? req.categorie() : Ticket.Categorie.GENERAL)
                .sujet(req.sujet())
                .message(req.message())
                .build();
        ticketRepository.save(ticket);
        return ResponseEntity.status(201).body(TicketResponse.from(ticket));
    }

    @GetMapping("/api/client/tickets")
    public List<TicketResponse> myTickets(Authentication authentication) {
        User user = currentUser(authentication);
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