package com.untitled.hermesapi.dto.category;

import com.untitled.hermesapi.model.enums.CategoryType;

public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        Long parentId,
        String color,
        String icon
) {
}
