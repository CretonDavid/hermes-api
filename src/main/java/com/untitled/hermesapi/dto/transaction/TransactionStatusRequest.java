package com.untitled.hermesapi.dto.transaction;

import com.untitled.hermesapi.model.enums.TransactionStatus;
import jakarta.validation.constraints.NotNull;

public record TransactionStatusRequest(
        @NotNull(message = "Le statut est obligatoire")
        TransactionStatus status
) {
}
