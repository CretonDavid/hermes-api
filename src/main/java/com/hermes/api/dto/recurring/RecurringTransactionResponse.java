package com.hermes.api.dto.recurring;

import com.hermes.api.dto.transaction.TransactionResponse;
import com.hermes.api.model.enums.PaymentMethod;
import com.hermes.api.model.enums.RecurrenceFrequency;
import com.hermes.api.model.enums.ReimbursementPeriod;
import com.hermes.api.model.enums.TransactionDirection;

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
        boolean transfer,
        String reimbursementPayer,
        Integer reimbursementShare,
        ReimbursementPeriod reimbursementPeriod
) {
}
