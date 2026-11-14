package com.hermes.api.dto.goal;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// Nouveau budget spécial, lié à un compte bancaire
public record GoalRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,
        @NotNull(message = "Le compte bancaire est obligatoire")
        Long accountId,
        @NotNull(message = "L'objectif est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "L'objectif doit être positif")
        @Digits(integer = 15, fraction = 2, message = "L'objectif doit avoir au plus 2 décimales")
        BigDecimal targetAmount,
        // Déjà mis de côté sur le compte, 0 par défaut
        @DecimalMin(value = "0", message = "Le montant de départ ne peut pas être négatif")
        @Digits(integer = 15, fraction = 2, message = "Le montant de départ doit avoir au plus 2 décimales")
        BigDecimal initialAmount
) {
}
