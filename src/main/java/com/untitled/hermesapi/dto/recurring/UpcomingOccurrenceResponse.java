package com.untitled.hermesapi.dto.recurring;

import com.untitled.hermesapi.model.enums.TransactionDirection;

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
