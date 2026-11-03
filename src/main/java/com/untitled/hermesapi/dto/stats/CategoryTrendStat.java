package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;
import java.time.YearMonth;

public record CategoryTrendStat(
        YearMonth month,
        BigDecimal amount
) {
}
