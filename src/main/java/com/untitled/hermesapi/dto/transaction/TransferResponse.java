package com.untitled.hermesapi.dto.transaction;

public record TransferResponse(
        TransactionResponse debit,
        TransactionResponse credit
) {
}
