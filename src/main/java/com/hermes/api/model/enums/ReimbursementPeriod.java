package com.hermes.api.model.enums;

import java.time.LocalDate;

/**
 * Rythme auquel un tiers rembourse une dépense récurrente. Les périodes suivent le calendrier :
 * mois, trimestre ou année civile.
 */
public enum ReimbursementPeriod {
    MONTHLY(1),
    QUARTERLY(3),
    YEARLY(12);

    private final int months;

    ReimbursementPeriod(int months) {
        this.months = months;
    }

    // Premier jour de la période contenant la date
    public LocalDate start(LocalDate date) {
        int firstMonth = (date.getMonthValue() - 1) / months * months + 1;
        return LocalDate.of(date.getYear(), firstMonth, 1);
    }

    public LocalDate next(LocalDate periodStart) {
        return periodStart.plusMonths(months);
    }

    public LocalDate end(LocalDate periodStart) {
        return next(periodStart).minusDays(1);
    }
}
