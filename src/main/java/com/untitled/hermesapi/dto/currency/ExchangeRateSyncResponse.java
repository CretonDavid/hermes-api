package com.untitled.hermesapi.dto.currency;

import java.util.List;

public record ExchangeRateSyncResponse(
        int imported,
        List<String> unsupportedCurrencies
) {
}
