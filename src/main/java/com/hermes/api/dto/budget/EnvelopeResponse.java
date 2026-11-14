package com.hermes.api.dto.budget;

import com.hermes.api.dto.transaction.TransactionResponse.CategorySummary;

import java.math.BigDecimal;

/**
 * Enveloppe d'un mois.
 *
 * @param amount       montant du mois
 * @param usualAmount  montant habituel, différent de amount si le mois a son propre montant
 * @param monthOnly    le montant est propre à ce mois
 * @param spent        dépenses du mois de la catégorie et de ses sous-catégories
 * @param remaining    amount - spent, négatif en cas de dépassement
 */
public record EnvelopeResponse(
        Long id,
        CategorySummary category,
        BigDecimal amount,
        BigDecimal usualAmount,
        boolean monthOnly,
        BigDecimal spent,
        BigDecimal remaining
) {
}
