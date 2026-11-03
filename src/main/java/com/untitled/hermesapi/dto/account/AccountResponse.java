package com.untitled.hermesapi.dto.account;

import com.untitled.hermesapi.dto.currency.CurrencyResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * balance : solde à date (opérations jusqu'à aujourd'hui),
 * forecastBalance : solde prévisionnel (toutes les opérations, y compris futures),
 * clearedBalance : solde pointé (opérations pointées ou rapprochées).
 */
public record AccountResponse(
        Long id,
        String name,
        String description,
        String bankName,
        AccountTypeResponse accountType,
        CurrencyResponse currency,
        BigDecimal initialBalance,
        BigDecimal balance,
        BigDecimal forecastBalance,
        BigDecimal clearedBalance,
        LocalDate openingDate,
        boolean closed
) {
}
