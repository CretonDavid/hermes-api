package com.hermes.api.dto.transaction;

public record TransferResponse(
        TransactionResponse debit,
        TransactionResponse credit
) {
}
