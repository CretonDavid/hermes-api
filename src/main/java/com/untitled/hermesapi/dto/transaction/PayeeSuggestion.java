package com.untitled.hermesapi.dto.transaction;

public record PayeeSuggestion(
        String payee,
        long usage,
        Long lastCategoryId
) {
}
