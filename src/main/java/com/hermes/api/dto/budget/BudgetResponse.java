package com.hermes.api.dto.budget;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Budget d'un mois, dans la devise de référence de l'utilisateur.
 *
 * @param budgeted   somme des enveloppes du mois
 * @param spent      dépenses du mois couvertes par une enveloppe
 * @param unbudgeted dépenses du mois hors enveloppe (non catégorisées comprises)
 */
public record BudgetResponse(
        YearMonth month,
        String currencyCode,
        List<String> missingRates,
        BigDecimal budgeted,
        BigDecimal spent,
        BigDecimal unbudgeted,
        List<EnvelopeResponse> envelopes
) {
}
