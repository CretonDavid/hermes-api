package com.hermes.api.dto.transaction;

import com.hermes.api.model.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Filtres de recherche des opérations. kind : EXPENSE (débits hors virements),
 * INCOME (crédits hors virements) ou TRANSFER.
 */
public record TransactionFilter(
        List<Long> accountIds,
        LocalDate from,
        LocalDate to,
        List<Long> categoryIds,
        Boolean uncategorized,
        TransactionKind kind,
        TransactionStatus status,
        String search,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        // Opérations générées par cette récurrence
        Long recurringTransactionId
) {

    public enum TransactionKind {
        EXPENSE,
        INCOME,
        TRANSFER
    }
}
