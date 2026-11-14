package com.hermes.api.dto.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;

// Nouvelle enveloppe : son montant vaut à partir du mois indiqué
public record EnvelopeRequest(
        @NotNull(message = "La catégorie est obligatoire")
        Long categoryId,
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le montant doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au plus 2 décimales")
        BigDecimal amount,
        @NotNull(message = "Le mois est obligatoire")
        YearMonth month
) {
}
