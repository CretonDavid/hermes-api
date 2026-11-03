package com.untitled.hermesapi.dto.transaction;

import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import com.untitled.hermesapi.model.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        String currencyCode,
        TransactionDirection direction,
        BigDecimal amount,
        String label,
        String payee,
        String notes,
        CategorySummary category,
        PaymentMethod paymentMethod,
        LocalDate operationDate,
        LocalDate valueDate,
        TransactionStatus status,
        TransferSummary transfer,
        Long recurringTransactionId,
        LocalDateTime createdAt
) {

    public record CategorySummary(Long id, String name, String color, String icon) {
    }

    public record TransferSummary(Long peerTransactionId, Long accountId, String accountName) {
    }
}
