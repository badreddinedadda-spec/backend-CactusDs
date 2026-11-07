package com.cactusds.backend.dto;

import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactureResponseTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 10, 1, 14, 0);

    private static Facture facture(Facture.Statut statut, LocalDate storedDue) {
        User user = User.builder().id(7L).email("client@cactusds.test").fullName("Client Test").build();
        return Facture.builder().id(1L).numero("FAC-2026-0001").user(user)
                .periodeDebut(LocalDate.of(2026, 10, 1)).periodeFin(LocalDate.of(2026, 10, 31))
                .montantTotal(new BigDecimal("1200.00")).statut(statut).dateEmission(ISSUED).dateEcheance(storedDue).build();
    }

    private static FactureResponse resp(Facture f, LocalDate today) {
        return FactureResponse.from(f, List.of(), today);
    }

    @Test
    void anUnpaidInvoiceIsOverdueOnlyAfterItsDueDate_notOnTheDueDateItself() {
        Facture f = facture(Facture.Statut.EMISE, null); // due 2026-10-08
        assertFalse(resp(f, LocalDate.of(2026, 10, 7)).enRetard());
        assertFalse(resp(f, LocalDate.of(2026, 10, 8)).enRetard());
        assertTrue(resp(f, LocalDate.of(2026, 10, 9)).enRetard());
    }

    @Test
    void paidOrCancelledInvoicesAreNeverOverdueAndNeverAnnounceASuspension() {
        for (Facture.Statut s : new Facture.Statut[]{Facture.Statut.PAYEE, Facture.Statut.ANNULEE}) {
            FactureResponse r = resp(facture(s, null), LocalDate.of(2027, 1, 1));
            assertFalse(r.enRetard());
            assertNull(r.dateSuspension());
        }
    }

    @Test
    void anUnpaidInvoiceAnnouncesTheSuspensionDate() {
        FactureResponse r = resp(facture(Facture.Statut.EMISE, null), LocalDate.of(2026, 10, 2));
        assertEquals(LocalDate.of(2026, 10, 16), r.dateSuspension());
    }

    @Test
    void theStoredDueDateIsUsedWhenPresentAndDerivedForOlderInvoices() {
        assertEquals(LocalDate.of(2026, 10, 20), resp(facture(Facture.Statut.EMISE, LocalDate.of(2026, 10, 20)), LocalDate.of(2026, 10, 15)).dateEcheance());
        assertFalse(resp(facture(Facture.Statut.EMISE, LocalDate.of(2026, 10, 20)), LocalDate.of(2026, 10, 15)).enRetard());
        assertEquals(LocalDate.of(2026, 10, 8), resp(facture(Facture.Statut.EMISE, null), LocalDate.of(2026, 10, 2)).dateEcheance());
    }

    @Test
    void aDeclaredBankTransferDoesNotClearTheOverdueFlagUntilAnAdminMarksTheInvoicePaid() {
        Facture f = facture(Facture.Statut.EMISE, null);
        f.setPaiementDeclareAt(LocalDateTime.of(2026, 10, 9, 9, 0));
        assertTrue(resp(f, LocalDate.of(2026, 10, 10)).enRetard());
    }

    @Test
    void existingFieldsAreUnchanged() {
        FactureResponse r = resp(facture(Facture.Statut.EMISE, null), LocalDate.of(2026, 10, 2));
        assertEquals("FAC-2026-0001", r.numero());
        assertEquals("client@cactusds.test", r.clientEmail());
        assertEquals("Client Test", r.clientNom());
        assertEquals("EMISE", r.statut());
        assertEquals(new BigDecimal("1200.00"), r.montantTotal());
        assertEquals(ISSUED, r.dateEmission());
    }
}