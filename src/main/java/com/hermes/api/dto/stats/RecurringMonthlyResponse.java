package com.hermes.api.dto.stats;

import java.math.BigDecimal;
import java.util.List;

/**
 * Récurrences converties dans la devise de référence.
 *
 * @param monthExpenses dépenses récurrentes du mois en cours : échéances déjà passées (montant réel)
 *                      et échéances à venir jusqu'à la fin du mois
 * @param expenses      coût mensuel moyen des dépenses récurrentes actives (une annuelle compte pour 1/12)
 * @param missingRates  devises sans taux de change : leurs montants ne sont pas comptés
 */
public record RecurringMonthlyResponse(
        String currencyCode,
        List<String> missingRates,
        BigDecimal monthExpenses,
        BigDecimal expenses,
        BigDecimal income,
        BigDecimal transfers
) {
}
