package com.hermes.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(

        @NotBlank(message = "Le numéro d'accès est obligatoire")
        @Pattern(regexp = "\\d{30}", message = "Le numéro d'accès doit contenir exactement 30 chiffres")
        String accountNumber

) {
}
