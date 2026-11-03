package com.untitled.hermesapi.service;

import com.untitled.hermesapi.model.ExchangeRate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Table de conversion en mémoire construite à partir des taux d'un utilisateur.
 * Pour une date donnée on utilise le dernier taux connu à cette date (ou, à défaut, le plus ancien
 * taux connu). Si aucun taux direct n'existe, on passe par une devise intermédiaire.
 */
public class RateTable {

    private static final MathContext PRECISION = new MathContext(16, RoundingMode.HALF_EVEN);

    // from -> to -> (date -> taux)
    private final Map<String, Map<String, NavigableMap<LocalDate, BigDecimal>>> rates = new HashMap<>();
    private final Set<String> missing = new TreeSet<>();

    public RateTable(Collection<ExchangeRate> exchangeRates) {
        for (ExchangeRate rate : exchangeRates) {
            String base = rate.getBaseCurrency().getCode();
            String quote = rate.getQuoteCurrency().getCode();
            put(base, quote, rate.getDate(), rate.getRate());
            put(quote, base, rate.getDate(), BigDecimal.ONE.divide(rate.getRate(), PRECISION));
        }
    }

    private void put(String from, String to, LocalDate date, BigDecimal rate) {
        rates.computeIfAbsent(from, k -> new HashMap<>())
                .computeIfAbsent(to, k -> new TreeMap<>())
                // un taux saisi directement prime sur un taux inversé à la même date
                .putIfAbsent(date, rate);
    }

    /**
     * Convertit un montant. Renvoie empty si aucun taux ne permet la conversion ; la devise
     * est alors mémorisée dans {@link #missingCurrencies()}.
     */
    public Optional<BigDecimal> convert(BigDecimal amount, String from, String to, LocalDate date) {
        if (from.equals(to) || amount.signum() == 0) {
            return Optional.of(amount);
        }
        Optional<BigDecimal> rate = rate(from, to, date);
        if (rate.isEmpty()) {
            missing.add(from);
            return Optional.empty();
        }
        return Optional.of(amount.multiply(rate.get(), PRECISION));
    }

    public BigDecimal convertOrZero(BigDecimal amount, String from, String to, LocalDate date) {
        return convert(amount, from, to, date).orElse(BigDecimal.ZERO);
    }

    public Optional<BigDecimal> rate(String from, String to, LocalDate date) {
        if (from.equals(to)) {
            return Optional.of(BigDecimal.ONE);
        }
        Optional<BigDecimal> direct = lookup(from, to, date);
        if (direct.isPresent()) {
            return direct;
        }
        // Triangulation par une devise intermédiaire (ex. USD -> EUR -> CHF)
        Map<String, NavigableMap<LocalDate, BigDecimal>> fromRates = rates.getOrDefault(from, Map.of());
        for (String intermediate : fromRates.keySet()) {
            Optional<BigDecimal> first = lookup(from, intermediate, date);
            Optional<BigDecimal> second = lookup(intermediate, to, date);
            if (first.isPresent() && second.isPresent()) {
                return Optional.of(first.get().multiply(second.get(), PRECISION));
            }
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> lookup(String from, String to, LocalDate date) {
        NavigableMap<LocalDate, BigDecimal> series = rates.getOrDefault(from, Map.of()).get(to);
        if (series == null || series.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<LocalDate, BigDecimal> entry = series.floorEntry(date);
        if (entry == null) {
            entry = series.firstEntry();
        }
        return Optional.of(entry.getValue());
    }

    public List<String> missingCurrencies() {
        return List.copyOf(missing);
    }
}
