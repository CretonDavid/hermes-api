package com.hermes.api.dto.reimbursement;

import com.hermes.api.model.enums.ReimbursementPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Suivi des remboursements d'une dépense récurrente, tiers par tiers puis période par période
 * (la plus récente d'abord).
 */
public record ReimbursementTrackingResponse(
        Long recurringTransactionId,
        String label,
        String currencyCode,
        List<PayerTracking> payers
) {

    public enum Status {
        // Intégralement remboursée à ce jour
        PAID,
        // Période terminée, remboursée en partie
        PARTIAL,
        // Période terminée, rien reçu
        UNPAID,
        // Période en cours, pas encore intégralement remboursée
        IN_PROGRESS
    }

    public record PayerTracking(
            Long payerId,
            String name,
            BigDecimal share,
            ReimbursementPeriod period,
            List<PeriodStatus> periods
    ) {
    }

    public record PeriodStatus(
            LocalDate start,
            LocalDate end,
            boolean ended,
            BigDecimal expenses,
            BigDecimal due,
            BigDecimal received,
            // Part due couverte par le trop-perçu des périodes précédentes
            BigDecimal advanceUsed,
            BigDecimal remaining,
            // Trop-perçu restant après cette période, reporté sur les suivantes
            BigDecimal surplus,
            Status status,
            List<Payment> payments
    ) {
    }

    public record Payment(Long transactionId, LocalDate date, BigDecimal amount, String label, String accountName) {
    }
}
