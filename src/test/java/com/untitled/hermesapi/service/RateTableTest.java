package com.untitled.hermesapi.service;

import com.untitled.hermesapi.model.Currency;
import com.untitled.hermesapi.model.ExchangeRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateTableTest {

    private static final Currency EUR = Currency.builder().id(1L).code("EUR").build();
    private static final Currency USD = Currency.builder().id(2L).code("USD").build();
    private static final Currency CHF = Currency.builder().id(3L).code("CHF").build();

    private static ExchangeRate rate(Currency base, Currency quote, String rate, String date) {
        return ExchangeRate.builder().baseCurrency(base).quoteCurrency(quote)
                .rate(new BigDecimal(rate)).date(LocalDate.parse(date)).build();
    }

    private static BigDecimal convert(RateTable table, String amount, String from, String to, String date) {
        return table.convert(new BigDecimal(amount), from, to, LocalDate.parse(date)).orElseThrow()
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    @Test
    void usesTheLatestRateKnownAtTheDate() {
        RateTable table = new RateTable(List.of(
                rate(EUR, USD, "1.10", "2026-01-01"),
                rate(EUR, USD, "1.20", "2026-02-01")));

        assertThat(convert(table, "100", "EUR", "USD", "2026-01-15")).isEqualByComparingTo("110.00");
        assertThat(convert(table, "100", "EUR", "USD", "2026-03-01")).isEqualByComparingTo("120.00");
        // Avant le premier taux connu : on utilise le plus ancien
        assertThat(convert(table, "100", "EUR", "USD", "2025-06-01")).isEqualByComparingTo("110.00");
    }

    @Test
    void invertsAndTriangulatesRates() {
        RateTable table = new RateTable(List.of(
                rate(EUR, USD, "1.25", "2026-01-01"),
                rate(EUR, CHF, "0.95", "2026-01-01")));

        assertThat(convert(table, "125", "USD", "EUR", "2026-01-10")).isEqualByComparingTo("100.00");
        // USD -> EUR -> CHF
        assertThat(convert(table, "125", "USD", "CHF", "2026-01-10")).isEqualByComparingTo("95.00");
    }

    @Test
    void reportsMissingCurrencies() {
        RateTable table = new RateTable(List.of());

        assertThat(table.convert(BigDecimal.TEN, "GBP", "EUR", LocalDate.now())).isEmpty();
        assertThat(table.convert(BigDecimal.TEN, "EUR", "EUR", LocalDate.now())).contains(BigDecimal.TEN);
        assertThat(table.missingCurrencies()).containsExactly("GBP");
    }
}
