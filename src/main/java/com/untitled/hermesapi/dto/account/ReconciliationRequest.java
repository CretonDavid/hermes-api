package com.untitled.hermesapi.dto.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconciliationRequest(
        @NotNull(message = "La date du relevé est obligatoire")
        LocalDate statementDate,
        @NotNull(message = "Le solde du relevé est obligatoire")
        @Digits(integer = 15, fraction = 2, message = "Le solde doit avoir au plus 2 décimales")
        BigDecimal statementBalance
) {
}
