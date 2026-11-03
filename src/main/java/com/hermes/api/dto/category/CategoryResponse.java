package com.hermes.api.dto.category;

import com.hermes.api.model.enums.CategoryType;

public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        Long parentId,
        String color,
        String icon
) {
}
