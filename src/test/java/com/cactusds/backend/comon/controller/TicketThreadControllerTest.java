package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.comon.ticket.TicketThreadService;
import com.cactusds.backend.controller.TicketThreadController;
import com.cactusds.backend.dto.TicketMessageResponse;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.TicketMessage;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.TicketRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TicketThreadControllerTest {

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketThreadService threads = mock(TicketThreadService.class);
    private final CurrentUserResolver users = mock(CurrentUserResolver.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("client@cactusds.test", null, List.of());
    private final Ticket ticket = Ticket.builder().id(11L).message("Initial").build();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TicketThreadController(tickets, threads, users, notifications)).build();        when(users.resolve(any())).thenReturn(User.builder().id(7L).email("client@cactusds.test").build());
        when(tickets.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(ticket));
        when(tickets.findByIdAndUserId(12L, 7L)).thenReturn(Optional.empty()); // someone else's ticket
    }

    @Test
    void theClientReadsTheThreadOfTheirOwnTicket() throws Exception {
        when(threads.thread(ticket)).thenReturn(List.of(
                new TicketMessageResponse("CLIENT", "Initial", LocalDateTime.of(2026, 9, 1, 10, 0)),
                new TicketMessageResponse("SUPPORT", "Answer", LocalDateTime.of(2026, 9, 2, 10, 0))));
        MvcResult r = mvc.perform(get("/api/client/tickets/11/messages").principal(auth)).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("\"auteur\":\"CLIENT\"") && body.contains("\"auteur\":\"SUPPORT\""));
    }

    @Test
    void aTicketThatIsNotTheCallersIsNotFound_forReadingAndForReplying() throws Exception {
        assertEquals(404, mvc.perform(get("/api/client/tickets/12/messages").principal(auth)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post("/api/client/tickets/12/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}")).andReturn().getResponse().getStatus());
        verify(threads, never()).appendClientMessage(any(), anyString());
    }

    @Test
    void aClientReplyIsStoredAndReturnedWith201() throws Exception {
        when(threads.appendClientMessage(ticket, "More details")).thenReturn(TicketMessage.builder()
                .auteur(TicketMessage.Auteur.CLIENT).message("More details").createdAt(LocalDateTime.of(2026, 9, 3, 9, 0)).build());
        MvcResult r = mvc.perform(post("/api/client/tickets/11/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"More details\"}")).andReturn();
        assertEquals(201, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("\"message\":\"More details\""));
    }
    @Test
    void aClientReplyAlertsSupport_butARejectedOneDoesNot() throws Exception {
        when(threads.appendClientMessage(ticket, "More details")).thenReturn(TicketMessage.builder()
                .auteur(TicketMessage.Auteur.CLIENT).message("More details").createdAt(LocalDateTime.now()).build());
        mvc.perform(post("/api/client/tickets/11/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"More details\"}")).andReturn();
        verify(notifications).notifyTicketClientReply(ticket, "More details");

        mvc.perform(post("/api/client/tickets/12/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"x\"}")).andReturn();
        mvc.perform(post("/api/client/tickets/11/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}")).andReturn();
        verify(notifications, org.mockito.Mockito.times(1)).notifyTicketClientReply(any(), anyString());
    }

    @Test
    void blankOrOversizedMessagesAreRejectedBeforeReachingTheService() throws Exception {
        assertEquals(400, mvc.perform(post("/api/client/tickets/11/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"   \"}")).andReturn().getResponse().getStatus());
        String huge = "x".repeat(5001);
        assertEquals(400, mvc.perform(post("/api/client/tickets/11/messages").principal(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + huge + "\"}")).andReturn().getResponse().getStatus());
        verify(threads, never()).appendClientMessage(any(), anyString());
    }

    @Test
    void theAdminCanReadAnyTicketThreadAndUnknownTicketsAre404() throws Exception {
        when(tickets.findById(11L)).thenReturn(Optional.of(ticket));
        when(tickets.findById(99L)).thenReturn(Optional.empty());
        when(threads.thread(ticket)).thenReturn(List.of(new TicketMessageResponse("CLIENT", "Initial", LocalDateTime.now())));
        assertEquals(200, mvc.perform(get("/api/admin/tickets/11/messages").principal(auth)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/admin/tickets/99/messages").principal(auth)).andReturn().getResponse().getStatus());
    }
}