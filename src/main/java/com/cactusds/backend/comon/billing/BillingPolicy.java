package com.cactusds.backend.comon.billing;

import com.cactusds.backend.model.Facture;
import java.time.LocalDate;
import java.time.LocalDateTime;
public final class BillingPolicy {
    public static final int PAYMENT_TERMS_DAYS = 7;
    public static final int SUSPENSION_AFTER_DAYS = 15;
    private BillingPolicy() {
    }

    public static LocalDate dueDate(LocalDateTime issuedAt) {
        return issuedAt.toLocalDate().plusDays(PAYMENT_TERMS_DAYS);
    }

    public static LocalDate suspensionDate(LocalDateTime issuedAt) {
        return issuedAt.toLocalDate().plusDays(SUSPENSION_AFTER_DAYS);
    }
    public static LocalDate dueDateOf(Facture facture) {
        if (facture.getDateEcheance() != null) {
            return facture.getDateEcheance();
        }
        return facture.getDateEmission() == null ? null : dueDate(facture.getDateEmission());
    }

}
