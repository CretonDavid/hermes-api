package com.hermes.api.dto.goal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Mouvement entre l'argent libre du compte et le budget spécial.
 *
 * @param amount positif pour affecter de l'argent libre au budget, négatif pour le rendre libre
 */
public record GoalAdjustmentRequest(
        @NotNull(message = "Le montant est obligatoire")
        @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au plus 2 décimales")
        BigDecimal amount
) {
}
