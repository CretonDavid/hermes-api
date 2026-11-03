package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;

public record PayeeStat(
        String payee,
        BigDecimal amount,
        long count
) {
}
