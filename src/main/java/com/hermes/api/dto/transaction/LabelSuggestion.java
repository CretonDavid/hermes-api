package com.hermes.api.dto.transaction;

/**
 * Libellé déjà utilisé, avec le dernier tiers et la dernière catégorie associés pour pré-remplir le formulaire.
 */
public record LabelSuggestion(
        String label,
        long usage,
        String lastPayee,
        Long lastCategoryId
) {
}
