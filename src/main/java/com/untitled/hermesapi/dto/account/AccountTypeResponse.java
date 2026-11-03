package com.untitled.hermesapi.dto.account;

public record AccountTypeResponse(
        Long id,
        String name,
        boolean savings
) {
}
