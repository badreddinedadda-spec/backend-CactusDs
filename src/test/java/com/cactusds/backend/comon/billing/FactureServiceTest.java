package com.cactusds.backend.comon.billing;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.FactureRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FactureServiceTest {

    private final FactureRepository factures = mock(FactureRepository.class);
    private final CommandeRepository commandes = mock(CommandeRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FactureService service = new FactureService(factures, commandes, notifications);

    @Test
    void aNewInvoiceGetsItsDueDateFixedAtIssueTime() {
        User client = User.builder().id(7L).email("client@cactusds.test").build();
        Commande c = Commande.builder().id(4L).prixTotal(new BigDecimal("100.00")).build();
        LocalDate from = LocalDate.of(2026, 10, 1), to = LocalDate.of(2026, 10, 31);
        when(commandes.findByUserIdAndFactureIsNullAndDateDebutBetween(7L, from, to)).thenReturn(List.of(c));

        Facture f = service.generateForPeriod(client, from, to);

        assertEquals(f.getDateEmission().toLocalDate().plusDays(BillingPolicy.PAYMENT_TERMS_DAYS), f.getDateEcheance());
        assertSame(f, c.getFacture());
        verify(notifications).notifyFactureGenerated(f);
    }
}