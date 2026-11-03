package com.untitled.hermesapi.dto.stats;

import java.util.List;

public record StatsListResponse<T>(
        String currencyCode,
        List<String> missingRates,
        List<T> items
) {
}
