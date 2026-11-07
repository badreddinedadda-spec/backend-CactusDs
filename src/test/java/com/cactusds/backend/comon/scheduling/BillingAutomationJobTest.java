package com.cactusds.backend.comon.scheduling;

import com.cactusds.backend.comon.billing.BillingPolicy;
import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.FactureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Guards the refactor that moved the 7 / 15 day constants into BillingPolicy: the job must behave exactly as before. */
class BillingAutomationJobTest {

    private final FactureRepository factures = mock(FactureRepository.class);
    private final CommandeRepository commandes = mock(CommandeRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private BillingAutomationJob job;

    @BeforeEach
    void setUp() {
        job = new BillingAutomationJob(factures, commandes, notifications);
    }

    private static boolean closeTo(LocalDateTime actual, LocalDateTime expected) {
        return Duration.between(actual, expected).abs().compareTo(Duration.ofMinutes(1)) < 0;
    }

    @Test
    void remindersUseTheSharedPaymentTermsAndFlagTheInvoice() {
        Facture f = Facture.builder().id(1L).statut(Facture.Statut.EMISE).build();
        when(factures.findByStatutAndDateEmissionBeforeAndRelanceEnvoyeeFalse(eq(Facture.Statut.EMISE), any())).thenReturn(List.of(f));
        job.relancerFacturesImpayees();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(factures).findByStatutAndDateEmissionBeforeAndRelanceEnvoyeeFalse(eq(Facture.Statut.EMISE), cutoff.capture());
        assertTrue(closeTo(cutoff.getValue(), LocalDateTime.now().minusDays(BillingPolicy.PAYMENT_TERMS_DAYS)));
        verify(notifications).notifyFactureOverdueReminder(f);
        assertTrue(f.getRelanceEnvoyee());
        verify(factures).save(f);
    }

    @Test
    void suspensionUsesTheSharedSuspensionDelayAndOnlySuspendsActiveServices() {
        Facture f = Facture.builder().id(1L).statut(Facture.Statut.EMISE).build();
        Commande active = Commande.builder().id(10L).statut(Commande.Statut.ACTIVE).build();
        Commande expired = Commande.builder().id(11L).statut(Commande.Statut.EXPIREE).build();
        when(factures.findByStatutAndDateEmissionBefore(eq(Facture.Statut.EMISE), any())).thenReturn(List.of(f));
        when(commandes.findByFactureIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(active, expired));
        job.suspendreServicesImpayes();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(factures).findByStatutAndDateEmissionBefore(eq(Facture.Statut.EMISE), cutoff.capture());
        assertTrue(closeTo(cutoff.getValue(), LocalDateTime.now().minusDays(BillingPolicy.SUSPENSION_AFTER_DAYS)));
        assertEquals(Commande.Statut.SUSPENDUE, active.getStatut());
        assertEquals(Commande.Statut.EXPIREE, expired.getStatut());
        verify(notifications, times(1)).notifyCommandeSuspended(active);
        verify(notifications, never()).notifyCommandeSuspended(expired);
    }
}