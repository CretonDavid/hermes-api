package com.untitled.hermesapi.dto.recurring;

import com.untitled.hermesapi.dto.transaction.TransactionResponse;
import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.RecurrenceFrequency;
import com.untitled.hermesapi.model.enums.TransactionDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        String currencyCode,
        Long targetAccountId,
        String targetAccountName,
        String targetCurrencyCode,
        TransactionDirection direction,
        BigDecimal amount,
        BigDecimal targetAmount,
        String label,
        String payee,
        TransactionResponse.CategorySummary category,
        PaymentMethod paymentMethod,
        RecurrenceFrequency frequency,
        int interval,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextDate,
        int occurrenceCount,
        boolean active,
        boolean transfer
) {
}
