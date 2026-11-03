package com.untitled.hermesapi.dto.category;

import com.untitled.hermesapi.model.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,
        @NotNull(message = "Le type est obligatoire")
        CategoryType type,
        Long parentId,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "La couleur doit être au format #RRGGBB")
        String color,
        @Size(max = 50, message = "L'icône ne doit pas dépasser 50 caractères")
        String icon
) {
}
