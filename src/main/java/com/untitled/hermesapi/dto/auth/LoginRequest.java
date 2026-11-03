package com.untitled.hermesapi.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(

        @NotBlank(message = "Le numéro de compte est obligatoire")
        @Pattern(regexp = "\\d{30}", message = "Le numéro de compte doit contenir exactement 30 chiffres")
        String accountNumber

) {
}
