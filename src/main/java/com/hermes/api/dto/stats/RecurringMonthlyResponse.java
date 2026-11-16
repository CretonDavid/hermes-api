package com.hermes.api.dto.stats;

import java.math.BigDecimal;
import java.util.List;

/**
 * Équivalent mensuel des récurrences actives, converti dans la devise de référence au taux du jour.
 *
 * @param missingRates devises sans taux de change : leurs récurrences ne sont pas comptées
 */
public record RecurringMonthlyResponse(
        String currencyCode,
        List<String> missingRates,
        BigDecimal expenses,
        BigDecimal income,
        BigDecimal transfers
) {
}
