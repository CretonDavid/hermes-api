package com.untitled.hermesapi.dto.importing;

import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une opération lue dans un fichier. Utilisée pour l'aperçu (avec duplicate et les suggestions)
 * puis renvoyée, éventuellement corrigée, lors de la confirmation.
 */
public record ImportRow(
        @NotNull(message = "La date est obligatoire")
        LocalDate operationDate,
        LocalDate valueDate,
        @NotBlank(message = "Le libellé est obligatoire")
        @Size(max = 255, message = "Le libellé ne doit pas dépasser 255 caractères")
        String label,
        @Size(max = 255, message = "Le tiers ne doit pas dépasser 255 caractères")
        String payee,
        @Size(max = 1000, message = "Les notes ne doivent pas dépasser 1000 caractères")
        String notes,
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le montant doit être positif")
        BigDecimal amount,
        @NotNull(message = "Le sens est obligatoire")
        TransactionDirection direction,
        Long categoryId,
        PaymentMethod paymentMethod,
        @Size(max = 255)
        String importId,
        boolean duplicate
) {
}
