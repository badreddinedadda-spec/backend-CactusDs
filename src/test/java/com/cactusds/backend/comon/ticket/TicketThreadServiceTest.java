package com.cactusds.backend.comon.ticket;

import com.cactusds.backend.dto.TicketMessageResponse;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.TicketMessage;
import com.cactusds.backend.repository.TicketMessageRepository;
import com.cactusds.backend.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketThreadServiceTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 1, 10, 0);
    private static final LocalDateTime REPLIED = LocalDateTime.of(2026, 9, 2, 11, 0);

    private final TicketMessageRepository messages = mock(TicketMessageRepository.class);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private TicketThreadService service;

    private static Ticket ticket(Ticket.Statut statut, String legacyReply) {
        return Ticket.builder().id(11L).message("Initial question").reponseAdmin(legacyReply)
                .statut(statut).createdAt(CREATED).updatedAt(REPLIED).build();
    }

    @BeforeEach
    void setUp() {
        service = new TicketThreadService(messages, tickets);
        when(messages.findByTicketIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of());
        when(messages.countByTicketId(11L)).thenReturn(0L);
        when(messages.save(any(TicketMessage.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aTicketWithoutAnyReplyIsJustItsInitialMessage() {
        List<TicketMessageResponse> t = service.thread(ticket(Ticket.Statut.OUVERT, null));
        assertEquals(1, t.size());
        assertEquals("CLIENT", t.get(0).auteur());
        assertEquals("Initial question", t.get(0).message());
        assertEquals(CREATED, t.get(0).createdAt());
    }

    @Test
    void aLegacyReplyShowsAsTheSecondEntryWhenThereAreNoThreadMessages() {
        List<TicketMessageResponse> t = service.thread(ticket(Ticket.Statut.RESOLU, "Old answer"));
        assertEquals(2, t.size());
        assertEquals("SUPPORT", t.get(1).auteur());
        assertEquals("Old answer", t.get(1).message());
        assertEquals(REPLIED, t.get(1).createdAt());
    }

    @Test
    void threadMessagesReplaceTheLegacyReplyAndKeepTheirOrder() {
        TicketMessage a = TicketMessage.builder().auteur(TicketMessage.Auteur.SUPPORT).message("Old answer").createdAt(REPLIED).build();
        TicketMessage b = TicketMessage.builder().auteur(TicketMessage.Auteur.CLIENT).message("Follow-up").createdAt(REPLIED.plusDays(1)).build();
        when(messages.findByTicketIdOrderByCreatedAtAscIdAsc(11L)).thenReturn(List.of(a, b));
        List<TicketMessageResponse> t = service.thread(ticket(Ticket.Statut.OUVERT, "Old answer"));
        assertEquals(List.of("CLIENT", "SUPPORT", "CLIENT"), t.stream().map(TicketMessageResponse::auteur).toList());
        assertEquals("Follow-up", t.get(2).message());
    }

    @Test
    void firstFollowUpMigratesTheLegacyReplyBeforeAddingTheNewMessage() {
        Ticket t = ticket(Ticket.Statut.EN_COURS, "Old answer");
        service.appendClientMessage(t, "  New question  ");
        ArgumentCaptor<TicketMessage> saved = ArgumentCaptor.forClass(TicketMessage.class);
        verify(messages, times(2)).save(saved.capture());
        TicketMessage migrated = saved.getAllValues().get(0);
        assertEquals(TicketMessage.Auteur.SUPPORT, migrated.getAuteur());
        assertEquals("Old answer", migrated.getMessage());
        assertEquals(REPLIED, migrated.getCreatedAt());
        TicketMessage added = saved.getAllValues().get(1);
        assertEquals(TicketMessage.Auteur.CLIENT, added.getAuteur());
        assertEquals("New question", added.getMessage());
    }

    @Test
    void noMigrationWhenThereIsNoLegacyReplyOrThreadAlreadyExists() {
        service.appendClientMessage(ticket(Ticket.Statut.OUVERT, null), "Hello");
        verify(messages, times(1)).save(any(TicketMessage.class));
        when(messages.countByTicketId(11L)).thenReturn(3L);
        service.appendClientMessage(ticket(Ticket.Statut.OUVERT, "Old answer"), "Again");
        verify(messages, times(2)).save(any(TicketMessage.class));
    }

    @Test
    void aClientFollowUpReopensAResolvedTicketButKeepsAnInProgressOne() {
        Ticket resolved = ticket(Ticket.Statut.RESOLU, null);
        service.appendClientMessage(resolved, "Still broken");
        assertEquals(Ticket.Statut.OUVERT, resolved.getStatut());
        Ticket inProgress = ticket(Ticket.Statut.EN_COURS, null);
        service.appendClientMessage(inProgress, "More details");
        assertEquals(Ticket.Statut.EN_COURS, inProgress.getStatut());
        verify(tickets, times(2)).save(any(Ticket.class));
    }

    @Test
    void aClosedTicketRefusesFollowUps() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.appendClientMessage(ticket(Ticket.Statut.FERME, null), "Hello?"));
        assertEquals(409, e.getStatusCode().value());
        verify(messages, never()).save(any());
    }

    @Test
    void aThreadIsCappedSoItCannotBeFloodedAndSupportRepliesAreCappedToo() {
        when(messages.countByTicketId(11L)).thenReturn(200L);
        assertThrows(ResponseStatusException.class, () -> service.appendClientMessage(ticket(Ticket.Statut.OUVERT, null), "spam"));
        assertThrows(ResponseStatusException.class, () -> service.appendSupportMessage(ticket(Ticket.Statut.OUVERT, null), "reply"));
        verify(messages, never()).save(any());
    }

    @Test
    void supportRepliesAreStoredAsSupportMessages() {
        TicketMessage m = service.appendSupportMessage(ticket(Ticket.Statut.OUVERT, null), "We are on it");
        assertEquals(TicketMessage.Auteur.SUPPORT, m.getAuteur());
        assertEquals("We are on it", m.getMessage());
    }
}