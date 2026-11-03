package com.untitled.hermesapi.dto.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReconciliationResponse(
        Long id,
        LocalDate statementDate,
        BigDecimal statementBalance,
        int transactionCount,
        LocalDateTime createdAt
) {
}
