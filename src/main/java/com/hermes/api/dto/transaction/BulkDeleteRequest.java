package com.hermes.api.dto.transaction;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Opérations à supprimer en une fois.
 */
public record BulkDeleteRequest(
        @NotEmpty(message = "Sélectionnez au moins une opération")
        @Size(max = 500, message = "500 opérations au maximum à la fois")
        List<Long> transactionIds
) {
}
