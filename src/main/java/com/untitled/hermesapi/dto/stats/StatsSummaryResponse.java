package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Montants exprimés dans la devise de référence de l'utilisateur. Les virements internes sont exclus
 * des revenus et des dépenses ; savings correspond aux virements nets vers les comptes d'épargne.
 * savingsRate = (revenus - dépenses) / revenus, null si aucun revenu.
 */
public record StatsSummaryResponse(
        String currencyCode,
        LocalDate from,
        LocalDate to,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal net,
        BigDecimal savings,
        BigDecimal savingsRate,
        BigDecimal previousIncome,
        BigDecimal previousExpenses,
        BigDecimal netWorth,
        List<String> missingRates
) {
}
