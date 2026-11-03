package com.hermes.api.dto.user;

import com.hermes.api.dto.currency.CurrencyResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        CurrencyResponse referenceCurrency
) {
}
