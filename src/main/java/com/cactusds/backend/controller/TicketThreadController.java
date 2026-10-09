package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.comon.ticket.TicketThreadService;
import com.cactusds.backend.dto.TicketMessageRequest;
import com.cactusds.backend.dto.TicketMessageResponse;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.TicketMessage;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.TicketRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class TicketThreadController {

    private final TicketRepository ticketRepository;
    private final TicketThreadService threadService;
    private final CurrentUserResolver currentUserResolver;
    private final NotificationService notificationService;
    public TicketThreadController(TicketRepository ticketRepository, TicketThreadService threadService,
                                  CurrentUserResolver currentUserResolver,  NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.threadService = threadService;
        this.currentUserResolver = currentUserResolver;
        this.notificationService = notificationService;

    }

    @GetMapping("/api/client/tickets/{id}/messages")
    public List<TicketMessageResponse> myThread(@PathVariable Long id, Authentication authentication) {
        return threadService.thread(ownTicket(id, authentication));
    }


    @PostMapping("/api/client/tickets/{id}/messages")
    public ResponseEntity<TicketMessageResponse> reply(
            @PathVariable Long id,
            @Valid @RequestBody TicketMessageRequest req,
            Authentication authentication) {

        Ticket ticket = ownTicket(id, authentication);

        TicketMessage saved =
                threadService.appendClientMessage(ticket, req.message());

        notificationService.notifyTicketClientReply(
                ticket, saved.getMessage());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TicketMessageResponse.from(saved));
    }
    @GetMapping("/api/admin/tickets/{id}/messages")
    public List<TicketMessageResponse> thread(@PathVariable Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found."));
        return threadService.thread(ticket);
    }

    private Ticket ownTicket(Long id, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ticketRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found."));
    }
}