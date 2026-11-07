package com.hermes.api.dto.reimbursement;

import com.hermes.api.model.enums.ReimbursementPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Suivi des remboursements d'une dépense récurrente, période par période (la plus récente d'abord).
 */
public record ReimbursementTrackingResponse(
        Long recurringTransactionId,
        String label,
        String payer,
        int share,
        ReimbursementPeriod period,
        String currencyCode,
        List<PeriodStatus> periods
) {

    public enum Status {
        // Période terminée ou en cours, intégralement remboursée à ce jour
        PAID,
        // Période terminée, remboursée en partie
        PARTIAL,
        // Période terminée, rien reçu
        UNPAID,
        // Période en cours, pas encore intégralement remboursée
        IN_PROGRESS
    }

    public record PeriodStatus(
            LocalDate start,
            LocalDate end,
            boolean ended,
            BigDecimal expenses,
            BigDecimal due,
            BigDecimal received,
            BigDecimal remaining,
            Status status,
            List<Payment> payments
    ) {
    }

    public record Payment(Long transactionId, LocalDate date, BigDecimal amount, String label, String accountName) {
    }
}
