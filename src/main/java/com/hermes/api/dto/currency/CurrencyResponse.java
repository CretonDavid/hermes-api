package com.hermes.api.dto.currency;

public record CurrencyResponse(
        Long id,
        String code,
        String symbol,
        String name,
        boolean custom
) {
}
