package com.hermes.api.dto.goal;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record GoalUpdateRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,
        @NotNull(message = "L'objectif est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "L'objectif doit être positif")
        @Digits(integer = 15, fraction = 2, message = "L'objectif doit avoir au plus 2 décimales")
        BigDecimal targetAmount
) {
}
