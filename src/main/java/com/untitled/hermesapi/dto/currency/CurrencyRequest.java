package com.untitled.hermesapi.dto.currency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CurrencyRequest(
        @NotBlank(message = "Le code de la devise est obligatoire")
        @Pattern(regexp = "[A-Za-z]{3}", message = "Le code doit contenir 3 lettres (ex : EUR)")
        String code,
        @NotBlank(message = "Le symbole est obligatoire")
        @Size(max = 10, message = "Le symbole ne doit pas dépasser 10 caractères")
        String symbol,
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name
) {
}
