package com.hermes.api.dto.transaction;

import com.hermes.api.model.enums.PaymentMethod;
import com.hermes.api.model.enums.ReimbursementPeriod;
import com.hermes.api.model.enums.TransactionDirection;
import com.hermes.api.model.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        String currencyCode,
        TransactionDirection direction,
        BigDecimal amount,
        String label,
        String payee,
        String notes,
        CategorySummary category,
        // Ventilation sur plusieurs catégories, vide sinon
        List<SplitSummary> splits,
        // Liste filtrée par catégorie : part de l'opération ventilée qui correspond au filtre, null sinon
        BigDecimal matchedAmount,
        // Part du crédit affectée à des budgets spéciaux
        List<GoalAllocationSummary> goalAllocations,
        PaymentMethod paymentMethod,
        LocalDate operationDate,
        LocalDate valueDate,
        TransactionStatus status,
        TransferSummary transfer,
        Long recurringTransactionId,
        ReimbursementSummary reimbursement,
        LocalDateTime createdAt
) {

    // Dépense récurrente remboursée par cette opération, tiers et début de la période couverte
    public record ReimbursementSummary(Long recurringTransactionId, String label, Long payerId, String payerName,
                                       ReimbursementPeriod period, LocalDate periodStart) {
    }

    public record CategorySummary(Long id, String name, String color, String icon) {
    }

    // category est null pour une part dont la catégorie a été supprimée
    public record GoalAllocationSummary(Long goalId, String goalName, BigDecimal amount) {
    }

    public record SplitSummary(CategorySummary category, String label, BigDecimal amount) {
    }

    public record TransferSummary(Long peerTransactionId, Long accountId, String accountName) {
    }
}
