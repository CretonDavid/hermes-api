package com.hermes.api.dto.transaction;

import com.hermes.api.model.enums.TransactionStatus;
import jakarta.validation.constraints.NotNull;

public record TransactionStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        TransactionStatus status
) {
}
