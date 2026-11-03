package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyStat(
        YearMonth month,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal net,
        BigDecimal savings
) {
}
