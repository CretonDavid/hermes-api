package com.hermes.api.dto.currency;

import java.util.List;

public record ExchangeRateSyncResponse(
        int imported,
        List<String> unsupportedCurrencies
) {
}
