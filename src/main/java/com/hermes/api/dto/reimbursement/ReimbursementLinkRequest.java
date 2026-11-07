package com.hermes.api.dto.reimbursement;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Rattache une opération reçue à la période de remboursement d'un tiers ; n'importe quelle date
 * de la période convient.
 */
public record ReimbursementLinkRequest(
        @NotNull(message = "Le tiers est obligatoire")
        Long payerId,
        @NotNull(message = "L'opération est obligatoire")
        Long transactionId,
        @NotNull(message = "La période est obligatoire")
        LocalDate period
) {
}
