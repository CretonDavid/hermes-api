package com.untitled.hermesapi.dto.user;

import com.untitled.hermesapi.dto.currency.CurrencyResponse;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        CurrencyResponse referenceCurrency
) {
}
