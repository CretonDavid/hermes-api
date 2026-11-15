package com.hermes.api.dto.goal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Part d'un crédit affectée à un budget spécial du compte crédité
public record GoalAllocationRequest(
        @NotNull(message = "Le budget spécial est obligatoire")
        Long goalId,
        @NotNull(message = "Le montant affecté est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le montant affecté doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant affecté doit avoir au plus 2 décimales")
        BigDecimal amount
) {
}
