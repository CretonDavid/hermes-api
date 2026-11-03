package com.hermes.api.dto.transaction;

public record PayeeSuggestion(
        String payee,
        long usage,
        Long lastCategoryId
) {
}
