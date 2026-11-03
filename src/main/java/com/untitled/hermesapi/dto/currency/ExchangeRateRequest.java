package com.untitled.hermesapi.dto.currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateRequest(
        @NotNull(message = "La devise de base est obligatoire")
        Long baseCurrencyId,
        @NotNull(message = "La devise de cotation est obligatoire")
        Long quoteCurrencyId,
        @NotNull(message = "Le taux est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le taux doit être positif")
        @Digits(integer = 11, fraction = 8, message = "Le taux doit avoir au plus 8 décimales")
        BigDecimal rate,
        @NotNull(message = "La date est obligatoire")
        LocalDate date
) {
}
