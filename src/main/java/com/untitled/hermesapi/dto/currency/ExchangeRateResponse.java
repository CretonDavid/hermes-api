package com.untitled.hermesapi.dto.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateResponse(
        Long id,
        CurrencyResponse baseCurrency,
        CurrencyResponse quoteCurrency,
        BigDecimal rate,
        LocalDate date
) {
}
