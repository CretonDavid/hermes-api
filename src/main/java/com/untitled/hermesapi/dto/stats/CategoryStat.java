package com.untitled.hermesapi.dto.stats;

import java.math.BigDecimal;

/**
 * categoryId null : opérations non catégorisées.
 * direct : opérations rattachées directement à la catégorie parente consultée (et non à une sous-catégorie).
 */
public record CategoryStat(
        Long categoryId,
        String name,
        String color,
        String icon,
        BigDecimal amount,
        BigDecimal share,
        long count,
        boolean hasChildren,
        boolean direct
) {
}
