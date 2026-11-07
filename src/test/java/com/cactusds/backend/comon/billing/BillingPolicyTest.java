package com.cactusds.backend.comon.billing;

import com.cactusds.backend.model.Facture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BillingPolicyTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 10, 1, 14, 30);

    @Test
    void theTermsAreTheOnesTheDailyJobAlreadyEnforced() {
        assertEquals(7, BillingPolicy.PAYMENT_TERMS_DAYS);
        assertEquals(15, BillingPolicy.SUSPENSION_AFTER_DAYS);
    }

    @Test
    void dueAndSuspensionDatesCountFromTheIssueDate() {
        assertEquals(LocalDate.of(2026, 10, 8), BillingPolicy.dueDate(ISSUED));
        assertEquals(LocalDate.of(2026, 10, 16), BillingPolicy.suspensionDate(ISSUED));
    }

    @Test
    void theStoredDueDateWinsAndOlderInvoicesDeriveIt() {
        Facture stored = Facture.builder().dateEmission(ISSUED).dateEcheance(LocalDate.of(2026, 10, 20)).build();
        assertEquals(LocalDate.of(2026, 10, 20), BillingPolicy.dueDateOf(stored));
        Facture legacy = Facture.builder().dateEmission(ISSUED).build();
        assertEquals(LocalDate.of(2026, 10, 8), BillingPolicy.dueDateOf(legacy));
    }

    @Test
    void noIssueDateMeansNoInventedDueDate() {
        Facture f = Facture.builder().dateEmission(null).build();
        assertNull(BillingPolicy.dueDateOf(f));
    }
}