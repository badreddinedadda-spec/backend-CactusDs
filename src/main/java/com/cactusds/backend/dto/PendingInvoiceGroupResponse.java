package com.cactusds.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PendingInvoiceGroupResponse(Long userId, String userEmail, String userFullName,
                                          int commandeCount, BigDecimal montantTotal,
                                          LocalDate periodeDebut, LocalDate periodeFin) {}