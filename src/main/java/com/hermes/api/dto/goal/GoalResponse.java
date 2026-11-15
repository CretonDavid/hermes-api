package com.hermes.api.dto.goal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Budget spécial et sa progression, dans la devise de son compte.
 *
 * @param savedAmount mis de côté aujourd'hui (au moment de l'utilisation pour un budget utilisé)
 * @param remaining   ce qu'il manque pour atteindre l'objectif, jamais négatif
 * @param reached     objectif atteint : le budget est verrouillé, il n'accepte plus d'affectation
 * @param withdrawn   repris par des retraits qui ont fait passer le solde du compte sous ses budgets
 */
public record GoalResponse(
        Long id,
        String name,
        Long accountId,
        String accountName,
        String currencyCode,
        BigDecimal targetAmount,
        BigDecimal initialAmount,
        BigDecimal savedAmount,
        BigDecimal remaining,
        boolean reached,
        BigDecimal withdrawn,
        LocalDate startDate,
        LocalDate usedDate
) {
}
