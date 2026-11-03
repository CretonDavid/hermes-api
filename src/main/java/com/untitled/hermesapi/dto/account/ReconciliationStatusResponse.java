package com.untitled.hermesapi.dto.account;

import java.math.BigDecimal;

/**
 * Etat du pointage d'un compte avant rapprochement.
 */
public record ReconciliationStatusResponse(
        BigDecimal clearedBalance,
        long clearedCount,
        ReconciliationResponse lastReconciliation
) {
}
