package com.hermes.api.dto.reimbursement;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Rattache une opération reçue à une période de remboursement ; n'importe quelle date de la période convient.
 */
public record ReimbursementLinkRequest(
        @NotNull(message = "L'opération est obligatoire")
        Long transactionId,
        @NotNull(message = "La période est obligatoire")
        LocalDate period
) {
}
