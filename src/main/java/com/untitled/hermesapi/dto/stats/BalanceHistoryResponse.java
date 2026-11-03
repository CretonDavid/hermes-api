package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * points : patrimoine total converti dans la devise de référence.
 * accounts : évolution du solde de chaque compte, dans sa propre devise.
 */
public record BalanceHistoryResponse(
        String currencyCode,
        List<String> missingRates,
        List<BalancePoint> points,
        List<AccountSeries> accounts
) {

    public record BalancePoint(LocalDate date, BigDecimal value) {
    }

    public record AccountSeries(Long accountId, String name, String currencyCode, boolean savings, List<BalancePoint> points) {
    }
}
