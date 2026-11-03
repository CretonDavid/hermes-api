package com.untitled.hermesapi.dto.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String name,
        @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
        String description,
        @Size(max = 100, message = "Le nom de la banque ne doit pas dépasser 100 caractères")
        String bankName,
        @NotNull(message = "Le type de compte est obligatoire")
        Long accountTypeId,
        @NotNull(message = "La devise est obligatoire")
        Long currencyId,
        @Digits(integer = 15, fraction = 2, message = "Le solde initial doit avoir au plus 2 décimales")
        BigDecimal initialBalance,
        LocalDate openingDate,
        boolean closed
) {
}
