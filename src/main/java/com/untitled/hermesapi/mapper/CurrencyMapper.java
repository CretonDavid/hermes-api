package com.untitled.hermesapi.mapper;

import com.untitled.hermesapi.dto.currency.CurrencyResponse;
import com.untitled.hermesapi.dto.currency.ExchangeRateResponse;
import com.untitled.hermesapi.model.Currency;
import com.untitled.hermesapi.model.ExchangeRate;
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
