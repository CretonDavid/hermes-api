package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * deposits : virements des comptes courants vers les comptes d'épargne,
 * withdrawals : virements des comptes d'épargne vers les comptes courants,
 * balance : encours total des comptes d'épargne en fin de mois.
 */
public record SavingsStatsResponse(
        String currencyCode,
        List<String> missingRates,
        BigDecimal totalDeposits,
        BigDecimal totalWithdrawals,
        BigDecimal net,
        BigDecimal currentSavingsBalance,
        BigDecimal savingsRate,
        List<SavingsMonth> months
) {

    public record SavingsMonth(YearMonth month, BigDecimal deposits, BigDecimal withdrawals, BigDecimal net,
                               BigDecimal balance, BigDecimal savingsRate) {
    }
}
