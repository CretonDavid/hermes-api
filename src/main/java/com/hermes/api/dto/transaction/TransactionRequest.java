package com.hermes.api.dto.transaction;

import com.hermes.api.model.enums.PaymentMethod;
import com.hermes.api.model.enums.TransactionDirection;
import com.hermes.api.model.enums.TransactionStatus;
import com.hermes.api.dto.goal.GoalAllocationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionRequest(
        @NotNull(message = "Le compte bancaire est obligatoire")
        Long accountId,
        @NotNull(message = "Le sens de l'opération est obligatoire")
        TransactionDirection direction,
        @NotNull(message = "Le montant est obligatoire")
        @DecimalMin(value = "0", inclusive = false, message = "Le montant doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Le montant doit avoir au plus 2 décimales")
        BigDecimal amount,
        @NotBlank(message = "Le libellé est obligatoire")
        @Size(max = 255, message = "Le libellé ne doit pas dépasser 255 caractères")
        String label,
        @Size(max = 255, message = "Le tiers ne doit pas dépasser 255 caractères")
        String payee,
        @Size(max = 1000, message = "Les notes ne doivent pas dépasser 1000 caractères")
        String notes,
        Long categoryId,
        // Ventilation sur plusieurs catégories (au moins 2 lignes), ou vide : categoryId est alors ignoré
        List<@Valid TransactionSplitRequest> splits,
        // Crédit affecté à des budgets spéciaux du compte
        List<@Valid GoalAllocationRequest> goalAllocations,
        PaymentMethod paymentMethod,
        @NotNull(message = "La date de l'opération est obligatoire")
        LocalDate operationDate,
        LocalDate valueDate,
        TransactionStatus status
) {
}
