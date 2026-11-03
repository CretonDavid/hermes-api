package com.untitled.hermesapi.dto.transaction;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferRequest(
        @NotNull(message = "Le compte débité est obligatoire")
        Long fromAccountId,
        @NotNull(message = "Le compte crédité est obligatoire")
        Long toAccountId,
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le montant doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au plus 2 décimales")
        BigDecimal amount,
        // Montant crédité si les deux comptes n'ont pas la même devise
        @DecimalMin(value = "0", inclusive = false, message = "Le montant crédité doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant crédité doit avoir au plus 2 décimales")
        BigDecimal targetAmount,
        @Size(max = 255, message = "Le libellé ne doit pas dépasser 255 caractères")
        String label,
        @Size(max = 1000, message = "Les notes ne doivent pas dépasser 1000 caractères")
        String notes,
        @NotNull(message = "La date est obligatoire")
        LocalDate operationDate
) {
}
