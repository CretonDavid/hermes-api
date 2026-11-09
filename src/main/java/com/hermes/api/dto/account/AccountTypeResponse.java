package com.hermes.api.dto.account;

public record AccountTypeResponse(
        Long id,
        String name,
        boolean savings
) {
}
