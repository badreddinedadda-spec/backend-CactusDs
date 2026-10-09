package com.cactusds.backend.comon.ticket;

import com.cactusds.backend.dto.TicketMessageResponse;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.TicketMessage;
import com.cactusds.backend.repository.TicketMessageRepository;
import com.cactusds.backend.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TicketThreadService {

    static final int MAX_MESSAGES_PER_TICKET = 200;

    private final TicketMessageRepository messages;
    private final TicketRepository tickets;

    public TicketThreadService(TicketMessageRepository messages, TicketRepository tickets) {
        this.messages = messages;
        this.tickets = tickets;
    }

    public List<TicketMessageResponse> thread(Ticket ticket) {
        List<TicketMessageResponse> out = new ArrayList<>();
        out.add(new TicketMessageResponse(TicketMessage.Auteur.CLIENT.name(), ticket.getMessage(), ticket.getCreatedAt()));
        List<TicketMessage> rows = messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        if (rows.isEmpty()) {
            if (ticket.getReponseAdmin() != null) {
                out.add(new TicketMessageResponse(TicketMessage.Auteur.SUPPORT.name(), ticket.getReponseAdmin(), legacyReplyDate(ticket)));
            }
        } else {
            rows.forEach(m -> out.add(TicketMessageResponse.from(m)));
        }
        return out;
    }

    /** A client follows up. Closed tickets are refused; a resolved ticket is reopened. */
    @Transactional
    public TicketMessage appendClientMessage(Ticket ticket, String body) {
        if (ticket.getStatut() == Ticket.Statut.FERME) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This ticket is closed. Please open a new ticket.");
        }
        TicketMessage saved = append(ticket, TicketMessage.Auteur.CLIENT, body);
        if (ticket.getStatut() == Ticket.Statut.RESOLU) {
            ticket.setStatut(Ticket.Statut.OUVERT);
        }
        ticket.setUpdatedAt(LocalDateTime.now());
        tickets.save(ticket);
        return saved;
    }


    @Transactional
    public TicketMessage appendSupportMessage(Ticket ticket, String body) {
        return append(ticket, TicketMessage.Auteur.SUPPORT, body);
    }

    private TicketMessage append(Ticket ticket, TicketMessage.Auteur auteur, String body) {
        long count = messages.countByTicketId(ticket.getId());
        if (count == 0 && ticket.getReponseAdmin() != null) {
            messages.save(TicketMessage.builder().ticket(ticket).auteur(TicketMessage.Auteur.SUPPORT)
                    .message(ticket.getReponseAdmin()).createdAt(legacyReplyDate(ticket)).build());
            count = 1;
        }
        if (count >= MAX_MESSAGES_PER_TICKET) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This ticket has reached the maximum number of messages. Please open a new ticket.");
        }
        return messages.save(TicketMessage.builder().ticket(ticket).auteur(auteur).message(body.trim()).build());
    }

    private static LocalDateTime legacyReplyDate(Ticket ticket) {
        return ticket.getUpdatedAt() != null ? ticket.getUpdatedAt() : ticket.getCreatedAt();
    }
}