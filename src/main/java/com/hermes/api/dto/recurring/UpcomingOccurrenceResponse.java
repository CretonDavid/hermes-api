package com.hermes.api.dto.recurring;

import com.hermes.api.model.enums.TransactionDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpcomingOccurrenceResponse(
        Long recurringTransactionId,
        LocalDate date,
        String label,
        TransactionDirection direction,
        BigDecimal amount,
        Long accountId,
        String accountName,
        String currencyCode,
        boolean transfer,
        String targetAccountName
) {
}
