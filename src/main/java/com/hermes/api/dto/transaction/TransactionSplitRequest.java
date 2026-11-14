package com.hermes.api.dto.transaction;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Part d'une opération ventilée : catégorie et montant, dans le sens de l'opération
public record TransactionSplitRequest(
        @NotNull(message = "Chaque ligne de la ventilation doit avoir une catégorie")
        Long categoryId,
        @NotNull(message = "Chaque ligne de la ventilation doit avoir un montant")
        @DecimalMin(value = "0", inclusive = false, message = "Chaque montant de la ventilation doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Les montants de la ventilation ont au plus 2 décimales")
        BigDecimal amount
) {
}
