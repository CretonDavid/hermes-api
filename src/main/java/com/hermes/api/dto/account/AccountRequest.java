package com.hermes.api.dto.account;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountRequest(
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 50, message = "Le nom ne doit pas dépasser 50 caractères")
        String name,
        @Size(max = 255, message = "La description ne doit pas dépasser 255 caractères")
        String description,
        @Size(max = 100, message = "Le nom de la banque ne doit pas dépasser 100 caractères")
        String bankName,
        @NotNull(message = "Le type de compte bancaire est obligatoire")
        Long accountTypeId,
        @NotNull(message = "La devise est obligatoire")
        Long currencyId,
        @Digits(integer = 15, fraction = 2, message = "Le solde initial doit avoir au plus 2 décimales")
        BigDecimal initialBalance,
        @Positive(message = "Le plafond doit être supérieur à 0")
        @Digits(integer = 15, fraction = 2, message = "Le plafond doit avoir au plus 2 décimales")
        BigDecimal ceiling,
        LocalDate openingDate,
        boolean closed
) {
}
