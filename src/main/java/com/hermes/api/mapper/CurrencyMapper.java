package com.hermes.api.mapper;

import com.hermes.api.dto.currency.CurrencyResponse;
import com.hermes.api.dto.currency.ExchangeRateResponse;
import com.hermes.api.model.Currency;
import com.hermes.api.model.ExchangeRate;
import org.springframework.stereotype.Component;

@Component
public class CurrencyMapper {

    public CurrencyResponse toResponse(Currency entity) {
        if (entity == null) {
            return null;
        }
        return new CurrencyResponse(
                entity.getId(),
                entity.getCode(),
                entity.getSymbol(),
                entity.getName(),
                entity.isCustom()
        );
    }

    public ExchangeRateResponse toResponse(ExchangeRate entity) {
        return new ExchangeRateResponse(
                entity.getId(),
                toResponse(entity.getBaseCurrency()),
                toResponse(entity.getQuoteCurrency()),
                entity.getRate().stripTrailingZeros(),
                entity.getDate()
        );
    }
}
