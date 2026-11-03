package com.untitled.hermesapi.dto.recurring;

import com.untitled.hermesapi.model.enums.PaymentMethod;
import com.untitled.hermesapi.model.enums.RecurrenceFrequency;
import com.untitled.hermesapi.model.enums.TransactionDirection;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Si targetAccountId est renseigné, la récurrence est un virement de accountId vers targetAccountId
 * et direction est ignorée.
 */
public record RecurringTransactionRequest(
        @NotNull(message = "Le compte est obligatoire")
        Long accountId,
        Long targetAccountId,
        TransactionDirection direction,
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le montant doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au plus 2 décimales")
        BigDecimal amount,
        @DecimalMin(value = "0", inclusive = false, message = "Le montant crédité doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant crédité doit avoir au plus 2 décimales")
        BigDecimal targetAmount,
        @NotBlank(message = "Le libellé est obligatoire")
        @Size(max = 255, message = "Le libellé ne doit pas dépasser 255 caractères")
        String label,
        @Size(max = 255, message = "Le tiers ne doit pas dépasser 255 caractères")
        String payee,
        Long categoryId,
        PaymentMethod paymentMethod,
        @NotNull(message = "La fréquence est obligatoire")
        RecurrenceFrequency frequency,
        @Min(value = 1, message = "L'intervalle doit être au moins de 1")
        @Max(value = 365, message = "L'intervalle ne doit pas dépasser 365")
        int interval,
        @NotNull(message = "La date de début est obligatoire")
        LocalDate startDate,
        LocalDate endDate,
        boolean active
) {
}
