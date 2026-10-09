package com.cactusds.backend.comon.controller;


import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.comon.ticket.TicketThreadService;
import com.cactusds.backend.controller.TicketController;
import com.cactusds.backend.dto.TicketReplyRequest;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.TicketMessage;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.TicketMessageRepository;
import com.cactusds.backend.repository.TicketRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketControllerReplyTest {

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketMessageRepository messages = mock(TicketMessageRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private TicketController controller;

    private static Ticket ticket(String legacyReply) {
        User user = User.builder().id(7L).email("client@cactusds.test").build();
        return Ticket.builder().id(11L).user(user).sujet("Subject").message("Initial").reponseAdmin(legacyReply)
                .statut(Ticket.Statut.OUVERT).createdAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 9, 2, 11, 0)).build();
    }

    @BeforeEach
    void setUp() {
        controller = new TicketController(tickets, notifications, mock(CurrentUserResolver.class),
                new TicketThreadService(messages, tickets));
        when(messages.countByTicketId(11L)).thenReturn(0L);
        when(messages.save(any(TicketMessage.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void theReplyJoinsTheConversationAndKeepsTheLegacyFieldsInSync() {
        Ticket t = ticket(null);
        when(tickets.findById(11L)).thenReturn(Optional.of(t));
        controller.reply(11L, new TicketReplyRequest("We are on it", Ticket.Statut.EN_COURS, null));
        ArgumentCaptor<TicketMessage> saved = ArgumentCaptor.forClass(TicketMessage.class);
        verify(messages, times(1)).save(saved.capture());
        assertEquals(TicketMessage.Auteur.SUPPORT, saved.getValue().getAuteur());
        assertEquals("We are on it", saved.getValue().getMessage());
        assertEquals("We are on it", t.getReponseAdmin());
        assertEquals(Ticket.Statut.EN_COURS, t.getStatut());
        verify(notifications).notifyTicketReply(t);
    }

    @Test
    void anOlderPreThreadReplyIsPreservedInTheConversationInsteadOfBeingLost() {
        Ticket t = ticket("Old answer");
        when(tickets.findById(11L)).thenReturn(Optional.of(t));
        controller.reply(11L, new TicketReplyRequest("New answer", null, null));
        ArgumentCaptor<TicketMessage> saved = ArgumentCaptor.forClass(TicketMessage.class);
        verify(messages, times(2)).save(saved.capture());
        assertEquals("Old answer", saved.getAllValues().get(0).getMessage());
        assertEquals("New answer", saved.getAllValues().get(1).getMessage());
        assertEquals("New answer", t.getReponseAdmin());
        assertEquals(Ticket.Statut.RESOLU, t.getStatut()); // unchanged default when no status is given
    }

    @Test
    void anUnknownTicketIsStillA404AndNothingIsStoredOrSent() {
        when(tickets.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, controller.reply(99L, new TicketReplyRequest("x", null, null)).getStatusCode().value());
        verify(messages, never()).save(any());
        verify(notifications, never()).notifyTicketReply(any());
    }
}