package com.hermes.api.dto.transaction;

import java.math.BigDecimal;

/**
 * Cumul des opérations correspondant à des filtres, pour une devise.
 */
public record TransactionTotals(
        String currencyCode,
        long count,
        BigDecimal credits,
        BigDecimal debits,
        BigDecimal net
) {
}
